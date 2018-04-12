package com.cloud.network.vpn;

import com.cloud.acl.SecurityChecker.AccessType;
import com.cloud.api.command.user.vpn.ListRemoteAccessVpnsCmd;
import com.cloud.api.command.user.vpn.ListVpnUsersCmd;
import com.cloud.configuration.Config;
import com.cloud.context.CallContext;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.exception.AccountLimitException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.framework.config.ConfigKey;
import com.cloud.framework.config.Configurable;
import com.cloud.framework.config.dao.ConfigurationDao;
import com.cloud.network.Network;
import com.cloud.network.Network.Service;
import com.cloud.network.NetworkModel;
import com.cloud.network.PublicIpAddress;
import com.cloud.network.RemoteAccessVpn;
import com.cloud.network.VpnUser;
import com.cloud.network.VpnUser.State;
import com.cloud.network.VpnUserVO;
import com.cloud.network.dao.FirewallRulesDao;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.IPAddressVO;
import com.cloud.network.dao.RemoteAccessVpnDao;
import com.cloud.network.dao.RemoteAccessVpnVO;
import com.cloud.network.dao.VpnUserDao;
import com.cloud.network.element.RemoteAccessVPNServiceProvider;
import com.cloud.network.rules.FirewallManager;
import com.cloud.network.rules.FirewallRule;
import com.cloud.network.rules.FirewallRule.Purpose;
import com.cloud.network.rules.FirewallRuleVO;
import com.cloud.network.rules.RulesManager;
import com.cloud.network.vpc.Vpc;
import com.cloud.network.vpc.dao.VpcDao;
import com.cloud.projects.Project.ListProjectResourcesCriteria;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.PasswordGenerator;
import com.cloud.utils.Ternary;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.JoinBuilder;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.SearchCriteria.Op;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionCallbackNoReturn;
import com.cloud.utils.db.TransactionCallbackWithException;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.exception.InvalidParameterValueException;
import com.cloud.utils.net.NetUtils;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteAccessVpnManagerImpl extends ManagerBase implements RemoteAccessVpnService, Configurable {
    static final ConfigKey<String> RemoteAccessVpnClientIpRange = new ConfigKey<>("Network", String.class, RemoteAccessVpnClientIpRangeCK, "10.1.2.1-10.1.2.8",
            "The range of ips to be allocated to remote access vpn clients. The first ip in the range is used by the VPN server", false, ConfigKey.Scope.Account);
    private final static Logger s_logger = LoggerFactory.getLogger(RemoteAccessVpnManagerImpl.class);
    @Inject
    AccountDao _accountDao;
    @Inject
    VpnUserDao _vpnUsersDao;
    @Inject
    RemoteAccessVpnDao _remoteAccessVpnDao;
    @Inject
    IPAddressDao _ipAddressDao;
    @Inject
    AccountManager _accountMgr;
    @Inject
    NetworkModel _networkMgr;
    @Inject
    RulesManager _rulesMgr;
    @Inject
    DomainDao _domainDao;
    @Inject
    FirewallRulesDao _rulesDao;
    @Inject
    FirewallManager _firewallMgr;
    @Inject
    ConfigurationDao _configDao;
    @Inject
    VpcDao _vpcDao;

    List<RemoteAccessVPNServiceProvider> _vpnServiceProviders;
    int _userLimit;
    int _pskLength;
    SearchBuilder<RemoteAccessVpnVO> VpnSearch;

    @Override
    @DB
    public RemoteAccessVpn createRemoteAccessVpn(final long publicIpId, String ipRange, final Boolean forDisplay) throws NetworkRuleConflictException {
        final CallContext ctx = CallContext.current();
        final Account caller = ctx.getCallingAccount();

        final Long networkId;

        // make sure ip address exists
        final PublicIpAddress ipAddr = _networkMgr.getPublicIpAddress(publicIpId);
        if (ipAddr == null) {
            throw new InvalidParameterValueException("Unable to create remote access vpn, invalid public IP address id" + publicIpId);
        }

        _accountMgr.checkAccess(caller, null, true, ipAddr);

        if (!ipAddr.readyToUse()) {
            throw new InvalidParameterValueException("The Ip address is not ready to be used yet: " + ipAddr.getAddress());
        }

        final IPAddressVO ipAddress = _ipAddressDao.findById(publicIpId);

        networkId = ipAddress.getAssociatedWithNetworkId();
        if (networkId != null) {
            _networkMgr.checkIpForService(ipAddress, Service.Vpn, null);
        }

        final Long vpcId = ipAddress.getVpcId();
        if (networkId == null && vpcId == null) {
            throw new InvalidParameterValueException("Unable to create remote access vpn for the ipAddress: " + ipAddr.getAddress().addr() +
                    " as ip is not associated with any network or VPC");
        }

        RemoteAccessVpnVO vpnVO = _remoteAccessVpnDao.findByPublicIpAddress(publicIpId);

        if (vpnVO != null) {
            //if vpn is in Added state, return it to the api
            if (vpnVO.getState() == RemoteAccessVpn.State.Added) {
                return vpnVO;
            }
            throw new InvalidParameterValueException("A Remote Access VPN already exists for this public Ip address");
        }

        if (ipRange == null) {
            ipRange = RemoteAccessVpnClientIpRange.valueIn(ipAddr.getAccountId());
        }
        final String[] range = ipRange.split("-");
        if (range.length != 2) {
            throw new InvalidParameterValueException("Invalid ip range");
        }
        if (!NetUtils.isValidIp4(range[0]) || !NetUtils.isValidIp4(range[1])) {
            throw new InvalidParameterValueException("Invalid ip in range specification " + ipRange);
        }
        if (!NetUtils.validIpRange(range[0], range[1])) {
            throw new InvalidParameterValueException("Invalid ip range " + ipRange);
        }

        final Pair<String, Integer> cidr;

        // TODO: assumes one virtual network / domr per account per zone
        if (networkId != null) {
            vpnVO = _remoteAccessVpnDao.findByAccountAndNetwork(ipAddr.getAccountId(), networkId);
            if (vpnVO != null) {
                //if vpn is in Added state, return it to the api
                if (vpnVO.getState() == RemoteAccessVpn.State.Added) {
                    return vpnVO;
                }
                throw new InvalidParameterValueException("A Remote Access VPN already exists for this account");
            }
            //Verify that vpn service is enabled for the network
            final Network network = _networkMgr.getNetwork(networkId);
            if (!_networkMgr.areServicesSupportedInNetwork(network.getId(), Service.Vpn)) {
                throw new InvalidParameterValueException("Vpn service is not supported in network id=" + ipAddr.getAssociatedWithNetworkId());
            }
            cidr = NetUtils.getCidr(network.getCidr());
        } else { // Don't need to check VPC because there is only one IP(source NAT IP) available for VPN
            final Vpc vpc = _vpcDao.findById(vpcId);
            cidr = NetUtils.getCidr(vpc.getCidr());
        }

        // FIXME: This check won't work for the case where the guest ip range
        // changes depending on the vlan allocated.
        final String[] guestIpRange = NetUtils.getIpRangeFromCidr(cidr.first(), cidr.second());
        if (NetUtils.ipRangesOverlap(range[0], range[1], guestIpRange[0], guestIpRange[1])) {
            throw new InvalidParameterValueException("Invalid ip range: " + ipRange + " overlaps with guest ip range " + guestIpRange[0] + "-" + guestIpRange[1]);
        }
        // TODO: check sufficient range
        // TODO: check overlap with private and public ip ranges in datacenter

        long startIp = NetUtils.ip2Long(range[0]);
        final String newIpRange = NetUtils.long2Ip(++startIp) + "-" + range[1];
        final String sharedSecret = PasswordGenerator.generatePresharedKey(_pskLength);

        return Transaction.execute(new TransactionCallbackWithException<RemoteAccessVpn, NetworkRuleConflictException>() {
            @Override
            public RemoteAccessVpn doInTransaction(final TransactionStatus status) throws NetworkRuleConflictException {
                if (vpcId == null) {
                    _rulesMgr.reservePorts(ipAddr, NetUtils.UDP_PROTO, Purpose.Vpn, caller, NetUtils.VPN_PORT, NetUtils.VPN_L2TP_PORT,
                            NetUtils.VPN_NATT_PORT);
                }
                final RemoteAccessVpnVO vpnVO =
                        new RemoteAccessVpnVO(ipAddr.getAccountId(), ipAddr.getDomainId(), ipAddr.getAssociatedWithNetworkId(), publicIpId, vpcId, range[0], newIpRange,
                                sharedSecret);

                if (forDisplay != null) {
                    vpnVO.setDisplay(forDisplay);
                }
                return _remoteAccessVpnDao.persist(vpnVO);
            }
        });
    }

    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_REMOTE_ACCESS_VPN_DESTROY, eventDescription = "removing remote access vpn", async = true)
    public boolean destroyRemoteAccessVpnForIp(final long ipId, final Account caller) throws ResourceUnavailableException {
        final RemoteAccessVpnVO vpn = _remoteAccessVpnDao.findByPublicIpAddress(ipId);
        if (vpn == null) {
            s_logger.debug("there are no Remote access vpns for public ip address id=" + ipId);
            return true;
        }

        _accountMgr.checkAccess(caller, AccessType.OperateEntry, true, vpn);

        final RemoteAccessVpn.State prevState = vpn.getState();
        vpn.setState(RemoteAccessVpn.State.Removed);
        _remoteAccessVpnDao.update(vpn.getId(), vpn);

        boolean success = false;
        try {
            for (final RemoteAccessVPNServiceProvider element : _vpnServiceProviders) {
                if (element.stopVpn(vpn)) {
                    success = true;
                    break;
                }
            }
        } catch (final ResourceUnavailableException ex) {
            vpn.setState(prevState);
            _remoteAccessVpnDao.update(vpn.getId(), vpn);
            s_logger.debug("Failed to stop the vpn " + vpn.getId() + " , so reverted state to " +
                    RemoteAccessVpn.State.Running);
            success = false;
        } finally {
            if (success) {
                //Cleanup corresponding ports
                final List<? extends FirewallRule> vpnFwRules = _rulesDao.listByIpAndPurpose(ipId, Purpose.Vpn);

                boolean applyFirewall = false;
                final List<FirewallRuleVO> fwRules = new ArrayList<>();
                //if related firewall rule is created for the first vpn port, it would be created for the 2 other ports as well, so need to cleanup the backend
                if (vpnFwRules.size() != 0 && _rulesDao.findByRelatedId(vpnFwRules.get(0).getId()) != null) {
                    applyFirewall = true;
                }

                if (applyFirewall) {
                    Transaction.execute(new TransactionCallbackNoReturn() {
                        @Override
                        public void doInTransactionWithoutResult(final TransactionStatus status) {
                            for (final FirewallRule vpnFwRule : vpnFwRules) {
                                //don't apply on the backend yet; send all 3 rules in a banch
                                _firewallMgr.revokeRelatedFirewallRule(vpnFwRule.getId(), false);
                                fwRules.add(_rulesDao.findByRelatedId(vpnFwRule.getId()));
                            }

                            s_logger.debug("Marked " + fwRules.size() + " firewall rules as Revoked as a part of disable remote access vpn");
                        }
                    });

                    //now apply vpn rules on the backend
                    s_logger.debug("Reapplying firewall rules for ip id=" + ipId + " as a part of disable remote access vpn");
                    success = _firewallMgr.applyIngressFirewallRules(ipId, caller);
                }

                if (success) {
                    try {
                        Transaction.execute(new TransactionCallbackNoReturn() {
                            @Override
                            public void doInTransactionWithoutResult(final TransactionStatus status) {
                                _remoteAccessVpnDao.remove(vpn.getId());
                                // Stop billing of VPN users when VPN is removed. VPN_User_ADD events will be generated when VPN is created again
                                if (vpnFwRules != null) {
                                    for (final FirewallRule vpnFwRule : vpnFwRules) {
                                        _rulesDao.remove(vpnFwRule.getId());
                                        s_logger.debug("Successfully removed firewall rule with ip id=" + vpnFwRule.getSourceIpAddressId() + " and port " +
                                                vpnFwRule.getSourcePortStart().intValue() + " as a part of vpn cleanup");
                                    }
                                }
                            }
                        });
                    } catch (final Exception ex) {
                        s_logger.warn("Unable to release the three vpn ports from the firewall rules", ex);
                    }
                }
            }
        }
        return success;
    }

    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_REMOTE_ACCESS_VPN_CREATE, eventDescription = "creating remote access vpn", async = true)
    public RemoteAccessVpnVO startRemoteAccessVpn(final long ipAddressId) throws ResourceUnavailableException {
        final Account caller = CallContext.current().getCallingAccount();

        final RemoteAccessVpnVO vpn = _remoteAccessVpnDao.findByPublicIpAddress(ipAddressId);
        if (vpn == null) {
            throw new InvalidParameterValueException("Unable to find your vpn: " + ipAddressId);
        }

        _accountMgr.checkAccess(caller, null, true, vpn);

        boolean started = false;
        try {
            boolean firewallOpened = true;

            if (firewallOpened) {
                for (final RemoteAccessVPNServiceProvider element : _vpnServiceProviders) {
                    if (element.startVpn(vpn)) {
                        started = true;
                        break;
                    }
                }
            }

            return vpn;
        } finally {
            if (started) {
                Transaction.execute(new TransactionCallbackNoReturn() {
                    @Override
                    public void doInTransactionWithoutResult(final TransactionStatus status) {
                        vpn.setState(RemoteAccessVpn.State.Running);
                        _remoteAccessVpnDao.update(vpn.getId(), vpn);
                    }
                });
            }
        }
    }

    @Override
    @DB
    public VpnUser addVpnUser(final long vpnOwnerId, final String username, final String password) {
        final Account caller = CallContext.current().getCallingAccount();

        if (!username.matches("^[a-zA-Z0-9][a-zA-Z0-9@._-]{2,63}$")) {
            throw new InvalidParameterValueException("Username has to be begin with an alphabet have 3-64 characters including alphabets, numbers and the set '@.-_'");
        }
        if (!password.matches("^[a-zA-Z0-9][a-zA-Z0-9@#+=._-]{2,31}$")) {
            throw new InvalidParameterValueException("Password has to be 3-32 characters including alphabets, numbers and the set '@#+=.-_'");
        }

        return Transaction.execute(new TransactionCallback<VpnUser>() {
            @Override
            public VpnUser doInTransaction(final TransactionStatus status) {
                final Account owner = _accountDao.lockRow(vpnOwnerId, true);
                if (owner == null) {
                    throw new InvalidParameterValueException("Unable to add vpn user: Another operation active");
                }
                _accountMgr.checkAccess(caller, null, true, owner);

                //don't allow duplicated user names for the same account
                final VpnUserVO vpnUser = _vpnUsersDao.findByAccountAndUsername(owner.getId(), username);
                if (vpnUser != null) {
                    throw new InvalidParameterValueException("VPN User with name " + username + " is already added for account " + owner);
                }

                final long userCount = _vpnUsersDao.getVpnUserCount(owner.getId());
                if (userCount >= _userLimit) {
                    throw new AccountLimitException("Cannot add more than " + _userLimit + " remote access vpn users");
                }

                return _vpnUsersDao.persist(new VpnUserVO(vpnOwnerId, owner.getDomainId(), username, password));
            }
        });
    }

    @DB
    @Override
    public boolean removeVpnUser(final long vpnOwnerId, final String username, final Account caller) {
        final VpnUserVO user = _vpnUsersDao.findByAccountAndUsername(vpnOwnerId, username);
        if (user == null) {
            throw new InvalidParameterValueException("Could not find vpn user " + username);
        }
        _accountMgr.checkAccess(caller, null, true, user);

        Transaction.execute(new TransactionCallbackNoReturn() {
            @Override
            public void doInTransactionWithoutResult(final TransactionStatus status) {
                user.setState(State.Revoke);
                _vpnUsersDao.update(user.getId(), user);
            }
        });

        return true;
    }

    @Override
    public List<? extends VpnUser> listVpnUsers(final long vpnOwnerId, final String userName) {
        final Account caller = CallContext.current().getCallingAccount();
        final Account owner = _accountDao.findById(vpnOwnerId);
        _accountMgr.checkAccess(caller, null, true, owner);
        return _vpnUsersDao.listByAccount(vpnOwnerId);
    }

    @DB
    @Override
    public boolean applyVpnUsers(final long vpnOwnerId, final String userName) {
        final Account caller = CallContext.current().getCallingAccount();
        final Account owner = _accountDao.findById(vpnOwnerId);
        _accountMgr.checkAccess(caller, null, true, owner);

        s_logger.debug("Applying vpn users for " + owner);
        final List<RemoteAccessVpnVO> vpns = _remoteAccessVpnDao.findByAccount(vpnOwnerId);

        if (vpns.size() == 0) {
            throw new InvalidParameterValueException("No Remote Access VPN configuration can be found for account " + owner.getAccountName());
        }

        final List<VpnUserVO> users = _vpnUsersDao.listByAccount(vpnOwnerId);

        //If user is in Active state, we still have to resend them therefore their status has to be Add
        for (final VpnUserVO user : users) {
            if (user.getState() == State.Active) {
                user.setState(State.Add);
                _vpnUsersDao.update(user.getId(), user);
            }
        }

        boolean success = true;

        final Boolean[] finals = new Boolean[users.size()];
        for (final RemoteAccessVPNServiceProvider element : _vpnServiceProviders) {
            s_logger.debug("Applying vpn access to " + element.getName());
            for (final RemoteAccessVpnVO vpn : vpns) {
                try {
                    final String[] results = element.applyVpnUsers(vpn, users);
                    if (results != null) {
                        int indexUser = -1;
                        for (int i = 0; i < results.length; i++) {
                            indexUser++;
                            if (indexUser == users.size()) {
                                // results on multiple VPC routers are combined in commit 13eb789, reset user index if one VR is done.
                                indexUser = 0;
                            }
                            s_logger.debug("VPN User " + users.get(indexUser) + (results[i] == null ? " is set on " : (" couldn't be set due to " + results[i]) + " on ") + vpn
                                    .getUuid());
                            if (results[i] == null) {
                                if (finals[indexUser] == null) {
                                    finals[indexUser] = true;
                                }
                            } else {
                                finals[indexUser] = false;
                                success = false;
                            }
                        }
                    }
                } catch (final Exception e) {
                    s_logger.warn("Unable to apply vpn users ", e);
                    success = false;

                    for (int i = 0; i < finals.length; i++) {
                        finals[i] = false;
                    }
                }
            }
        }

        for (int i = 0; i < finals.length; i++) {
            final VpnUserVO user = users.get(i);
            if (finals[i]) {
                if (user.getState() == State.Add) {
                    user.setState(State.Active);
                    _vpnUsersDao.update(user.getId(), user);
                } else if (user.getState() == State.Revoke) {
                    _vpnUsersDao.remove(user.getId());
                }
            } else {
                if (user.getState() == State.Add && (user.getUsername()).equals(userName)) {
                    Transaction.execute(new TransactionCallbackNoReturn() {
                        @Override
                        public void doInTransactionWithoutResult(final TransactionStatus status) {
                            _vpnUsersDao.remove(user.getId());
                        }
                    });
                }
                s_logger.warn("Failed to apply vpn for user " + user.getUsername() + ", accountId=" + user.getAccountId());
            }
        }

        return success;
    }

    @Override
    public Pair<List<? extends RemoteAccessVpn>, Integer> searchForRemoteAccessVpns(final ListRemoteAccessVpnsCmd cmd) {
        // do some parameter validation
        final Account caller = CallContext.current().getCallingAccount();
        final Long ipAddressId = cmd.getPublicIpId();
        final List<Long> permittedAccounts = new ArrayList<>();

        final Long vpnId = cmd.getId();
        final Long networkId = cmd.getNetworkId();

        if (ipAddressId != null) {
            final PublicIpAddress publicIp = _networkMgr.getPublicIpAddress(ipAddressId);
            if (publicIp == null) {
                throw new InvalidParameterValueException("Unable to list remote access vpns, IP address " + ipAddressId + " not found.");
            } else {
                final Long ipAddrAcctId = publicIp.getAccountId();
                if (ipAddrAcctId == null) {
                    throw new InvalidParameterValueException("Unable to list remote access vpns, IP address " + ipAddressId + " is not associated with an account.");
                }
            }
            _accountMgr.checkAccess(caller, null, true, publicIp);
        }

        final Ternary<Long, Boolean, ListProjectResourcesCriteria> domainIdRecursiveListProject = new Ternary<>(cmd.getDomainId(), cmd
                .isRecursive(), null);
        _accountMgr.buildACLSearchParameters(caller, null, cmd.getAccountName(), cmd.getProjectId(), permittedAccounts, domainIdRecursiveListProject, cmd.listAll(), false);
        final Long domainId = domainIdRecursiveListProject.first();
        final Boolean isRecursive = domainIdRecursiveListProject.second();
        final ListProjectResourcesCriteria listProjectResourcesCriteria = domainIdRecursiveListProject.third();

        final Filter filter = new Filter(RemoteAccessVpnVO.class, "serverAddressId", false, cmd.getStartIndex(), cmd.getPageSizeVal());
        final SearchBuilder<RemoteAccessVpnVO> sb = _remoteAccessVpnDao.createSearchBuilder();
        _accountMgr.buildACLSearchBuilder(sb, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);

        sb.and("serverAddressId", sb.entity().getServerAddressId(), Op.EQ);
        sb.and("id", sb.entity().getId(), Op.EQ);
        sb.and("networkId", sb.entity().getNetworkId(), Op.EQ);
        sb.and("state", sb.entity().getState(), Op.EQ);
        sb.and("display", sb.entity().isDisplay(), Op.EQ);

        final SearchCriteria<RemoteAccessVpnVO> sc = sb.create();
        _accountMgr.buildACLSearchCriteria(sc, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);

        sc.setParameters("state", RemoteAccessVpn.State.Running);

        if (ipAddressId != null) {
            sc.setParameters("serverAddressId", ipAddressId);
        }

        if (vpnId != null) {
            sc.setParameters("id", vpnId);
        }

        if (networkId != null) {
            sc.setParameters("networkId", networkId);
        }

        final Pair<List<RemoteAccessVpnVO>, Integer> result = _remoteAccessVpnDao.searchAndCount(sc, filter);
        return new Pair<>(result.first(), result.second());
    }

    @Override
    public Pair<List<? extends VpnUser>, Integer> searchForVpnUsers(final ListVpnUsersCmd cmd) {
        final String username = cmd.getUsername();
        final Long id = cmd.getId();
        final Account caller = CallContext.current().getCallingAccount();
        final List<Long> permittedAccounts = new ArrayList<>();

        final Ternary<Long, Boolean, ListProjectResourcesCriteria> domainIdRecursiveListProject = new Ternary<>(cmd.getDomainId(), cmd
                .isRecursive(), null);
        _accountMgr.buildACLSearchParameters(caller, id, cmd.getAccountName(), cmd.getProjectId(), permittedAccounts, domainIdRecursiveListProject, cmd.listAll(), false);
        final Long domainId = domainIdRecursiveListProject.first();
        final Boolean isRecursive = domainIdRecursiveListProject.second();
        final ListProjectResourcesCriteria listProjectResourcesCriteria = domainIdRecursiveListProject.third();
        final Filter searchFilter = new Filter(VpnUserVO.class, "username", true, cmd.getStartIndex(), cmd.getPageSizeVal());
        final SearchBuilder<VpnUserVO> sb = _vpnUsersDao.createSearchBuilder();
        _accountMgr.buildACLSearchBuilder(sb, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);

        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("username", sb.entity().getUsername(), SearchCriteria.Op.EQ);
        sb.and("state", sb.entity().getState(), Op.IN);

        final SearchCriteria<VpnUserVO> sc = sb.create();
        _accountMgr.buildACLSearchCriteria(sc, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);

        //list only active users
        sc.setParameters("state", State.Active, State.Add);

        if (id != null) {
            sc.setParameters("id", id);
        }

        if (username != null) {
            sc.setParameters("username", username);
        }

        final Pair<List<VpnUserVO>, Integer> result = _vpnUsersDao.searchAndCount(sc, searchFilter);
        return new Pair<>(result.first(), result.second());
    }

    @Override
    public List<? extends RemoteAccessVpn> listRemoteAccessVpns(final long networkId) {
        return _remoteAccessVpnDao.listByNetworkId(networkId);
    }

    @Override
    public RemoteAccessVpn getRemoteAccessVpn(final long vpnAddrId) {
        return _remoteAccessVpnDao.findByPublicIpAddress(vpnAddrId);
    }

    @Override
    public RemoteAccessVpn getRemoteAccessVpnById(final long vpnId) {
        return _remoteAccessVpnDao.findById(vpnId);
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_REMOTE_ACCESS_VPN_UPDATE, eventDescription = "updating remote access vpn", async = true)
    public RemoteAccessVpn updateRemoteAccessVpn(final long id, final String customId, final Boolean forDisplay) {
        final RemoteAccessVpnVO vpn = _remoteAccessVpnDao.findById(id);
        if (vpn == null) {
            throw new InvalidParameterValueException("Can't find remote access vpn by id " + id);
        }

        _accountMgr.checkAccess(CallContext.current().getCallingAccount(), null, true, vpn);
        if (customId != null) {
            vpn.setUuid(customId);
        }
        if (forDisplay != null) {
            vpn.setDisplay(forDisplay);
        }

        _remoteAccessVpnDao.update(vpn.getId(), vpn);
        return _remoteAccessVpnDao.findById(id);
    }

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        final Map<String, String> configs = _configDao.getConfiguration(params);

        _userLimit = NumbersUtil.parseInt(configs.get(Config.RemoteAccessVpnUserLimit.key()), 8);

        _pskLength = NumbersUtil.parseInt(configs.get(Config.RemoteAccessVpnPskLength.key()), 24);

        validateRemoteAccessVpnConfiguration();

        VpnSearch = _remoteAccessVpnDao.createSearchBuilder();
        VpnSearch.and("accountId", VpnSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        final SearchBuilder<DomainVO> domainSearch = _domainDao.createSearchBuilder();
        domainSearch.and("path", domainSearch.entity().getPath(), SearchCriteria.Op.LIKE);
        VpnSearch.join("domainSearch", domainSearch, VpnSearch.entity().getDomainId(), domainSearch.entity().getId(), JoinBuilder.JoinType.INNER);
        VpnSearch.done();

        return true;
    }

    private void validateRemoteAccessVpnConfiguration() throws ConfigurationException {
        final String ipRange = RemoteAccessVpnClientIpRange.value();
        if (ipRange == null) {
            s_logger.warn("Remote Access VPN global configuration missing client ip range -- ignoring");
            return;
        }
        final Integer pskLength = _pskLength;
        if (pskLength != null && (pskLength < 8 || pskLength > 256)) {
            throw new ConfigurationException("Remote Access VPN: IPSec preshared key length should be between 8 and 256");
        }

        final String[] range = ipRange.split("-");
        if (range.length != 2) {
            throw new ConfigurationException("Remote Access VPN: Invalid ip range " + ipRange);
        }
        if (!NetUtils.isValidIp4(range[0]) || !NetUtils.isValidIp4(range[1])) {
            throw new ConfigurationException("Remote Access VPN: Invalid ip in range specification " + ipRange);
        }
        if (!NetUtils.validIpRange(range[0], range[1])) {
            throw new ConfigurationException("Remote Access VPN: Invalid ip range " + ipRange);
        }
    }

    public List<RemoteAccessVPNServiceProvider> getRemoteAccessVPNServiceProviders() {
        final List<RemoteAccessVPNServiceProvider> result = new ArrayList<>();
        for (final Iterator<RemoteAccessVPNServiceProvider> e = _vpnServiceProviders.iterator(); e.hasNext(); ) {
            result.add(e.next());
        }

        return result;
    }

    @Override
    public String getConfigComponentName() {
        return RemoteAccessVpnService.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{RemoteAccessVpnClientIpRange};
    }

    public List<RemoteAccessVPNServiceProvider> getVpnServiceProviders() {
        return _vpnServiceProviders;
    }

    public void setVpnServiceProviders(final List<RemoteAccessVPNServiceProvider> vpnServiceProviders) {
        _vpnServiceProviders = vpnServiceProviders;
    }
}
