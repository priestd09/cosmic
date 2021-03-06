package com.cloud.resourcelimit;

import com.cloud.acl.SecurityChecker.AccessType;
import com.cloud.alert.AlertManager;
import com.cloud.common.managed.context.ManagedContextRunnable;
import com.cloud.configuration.Config;
import com.cloud.configuration.ResourceCountVO;
import com.cloud.configuration.ResourceLimitVO;
import com.cloud.configuration.dao.ResourceCountDao;
import com.cloud.configuration.dao.ResourceLimitDao;
import com.cloud.context.CallContext;
import com.cloud.dao.EntityManager;
import com.cloud.dc.VlanVO;
import com.cloud.dc.dao.VlanDao;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.framework.config.dao.ConfigurationDao;
import com.cloud.legacymodel.configuration.Resource;
import com.cloud.legacymodel.configuration.Resource.ResourceOwnerType;
import com.cloud.legacymodel.configuration.Resource.ResourceType;
import com.cloud.legacymodel.configuration.ResourceCount;
import com.cloud.legacymodel.domain.Domain;
import com.cloud.legacymodel.exceptions.CloudRuntimeException;
import com.cloud.legacymodel.exceptions.InvalidParameterValueException;
import com.cloud.legacymodel.exceptions.PermissionDeniedException;
import com.cloud.legacymodel.exceptions.ResourceAllocationException;
import com.cloud.legacymodel.storage.ObjectInDataStoreStateMachine;
import com.cloud.legacymodel.storage.VMTemplateStatus;
import com.cloud.legacymodel.user.Account;
import com.cloud.legacymodel.vm.VirtualMachine.State;
import com.cloud.model.enumeration.DataStoreRole;
import com.cloud.model.enumeration.VirtualMachineType;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.IPAddressVO;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.vpc.dao.VpcDao;
import com.cloud.projects.Project;
import com.cloud.projects.ProjectAccount.Role;
import com.cloud.projects.dao.ProjectAccountDao;
import com.cloud.projects.dao.ProjectDao;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.storage.SnapshotVO;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.dao.SnapshotDao;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.storage.dao.VolumeDaoImpl.SumCount;
import com.cloud.storage.datastore.db.SnapshotDataStoreDao;
import com.cloud.storage.datastore.db.SnapshotDataStoreVO;
import com.cloud.storage.datastore.db.TemplateDataStoreDao;
import com.cloud.storage.datastore.db.TemplateDataStoreVO;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountVO;
import com.cloud.user.ResourceLimitService;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GenericSearchBuilder;
import com.cloud.utils.db.JoinBuilder;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.SearchCriteria.Func;
import com.cloud.utils.db.SearchCriteria.Op;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionCallbackWithExceptionNoReturn;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.VMInstanceDao;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ResourceLimitManagerImpl extends ManagerBase implements ResourceLimitService {
    public static final Logger s_logger = LoggerFactory.getLogger(ResourceLimitManagerImpl.class);
    @Inject
    protected SnapshotDao _snapshotDao;
    @Inject
    protected VMTemplateDao _vmTemplateDao;
    protected GenericSearchBuilder<TemplateDataStoreVO, SumCount> templateSizeSearch;
    protected GenericSearchBuilder<SnapshotDataStoreVO, SumCount> snapshotSizeSearch;
    protected SearchBuilder<ResourceCountVO> ResourceCountSearch;
    ScheduledExecutorService _rcExecutor;
    long _resourceCountCheckInterval = 0;
    Map<ResourceType, Long> accountResourceLimitMap = new EnumMap<>(ResourceType.class);
    Map<ResourceType, Long> domainResourceLimitMap = new EnumMap<>(ResourceType.class);
    Map<ResourceType, Long> projectResourceLimitMap = new EnumMap<>(ResourceType.class);
    @Inject
    private DomainDao _domainDao;
    @Inject
    private AccountManager _accountMgr;
    @Inject
    private AlertManager _alertMgr;
    @Inject
    private ResourceCountDao _resourceCountDao;
    @Inject
    private ResourceLimitDao _resourceLimitDao;
    @Inject
    private UserVmDao _userVmDao;
    @Inject
    private AccountDao _accountDao;
    @Inject
    private VolumeDao _volumeDao;
    @Inject
    private IPAddressDao _ipAddressDao;
    @Inject
    private VMInstanceDao _vmDao;
    @Inject
    private ConfigurationDao _configDao;
    @Inject
    private EntityManager _entityMgr;
    @Inject
    private ProjectDao _projectDao;
    @Inject
    private ProjectAccountDao _projectAccountDao;
    @Inject
    private NetworkDao _networkDao;
    @Inject
    private VpcDao _vpcDao;
    @Inject
    private ServiceOfferingDao _serviceOfferingDao;
    @Inject
    private TemplateDataStoreDao _vmTemplateStoreDao;
    @Inject
    private VlanDao _vlanDao;
    @Inject
    private SnapshotDataStoreDao _snapshotDataStoreDao;

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {

        this.ResourceCountSearch = this._resourceCountDao.createSearchBuilder();
        this.ResourceCountSearch.and("id", this.ResourceCountSearch.entity().getId(), SearchCriteria.Op.IN);
        this.ResourceCountSearch.and("accountId", this.ResourceCountSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        this.ResourceCountSearch.and("domainId", this.ResourceCountSearch.entity().getDomainId(), SearchCriteria.Op.EQ);
        this.ResourceCountSearch.done();

        this.templateSizeSearch = this._vmTemplateStoreDao.createSearchBuilder(SumCount.class);
        this.templateSizeSearch.select("sum", Func.SUM, this.templateSizeSearch.entity().getSize());
        this.templateSizeSearch.and("downloadState", this.templateSizeSearch.entity().getDownloadState(), Op.EQ);
        this.templateSizeSearch.and("destroyed", this.templateSizeSearch.entity().getDestroyed(), Op.EQ);
        final SearchBuilder<VMTemplateVO> join1 = this._vmTemplateDao.createSearchBuilder();
        join1.and("accountId", join1.entity().getAccountId(), Op.EQ);
        this.templateSizeSearch.join("templates", join1, this.templateSizeSearch.entity().getTemplateId(), join1.entity().getId(), JoinBuilder.JoinType.INNER);
        this.templateSizeSearch.done();

        this.snapshotSizeSearch = this._snapshotDataStoreDao.createSearchBuilder(SumCount.class);
        this.snapshotSizeSearch.select("sum", Func.SUM, this.snapshotSizeSearch.entity().getPhysicalSize());
        this.snapshotSizeSearch.and("state", this.snapshotSizeSearch.entity().getState(), Op.EQ);
        this.snapshotSizeSearch.and("storeRole", this.snapshotSizeSearch.entity().getRole(), Op.EQ);
        final SearchBuilder<SnapshotVO> join2 = this._snapshotDao.createSearchBuilder();
        join2.and("accountId", join2.entity().getAccountId(), Op.EQ);
        this.snapshotSizeSearch.join("snapshots", join2, this.snapshotSizeSearch.entity().getSnapshotId(), join2.entity().getId(), JoinBuilder.JoinType.INNER);
        this.snapshotSizeSearch.done();

        this._resourceCountCheckInterval = NumbersUtil.parseInt(this._configDao.getValue(Config.ResourceCountCheckInterval.key()), 0);
        if (this._resourceCountCheckInterval > 0) {
            this._rcExecutor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("ResourceCountChecker"));
        }

        try {
            this.projectResourceLimitMap.put(Resource.ResourceType.public_ip, Long.parseLong(this._configDao.getValue(Config.DefaultMaxProjectPublicIPs.key())));
            this.projectResourceLimitMap.put(Resource.ResourceType.snapshot, Long.parseLong(this._configDao.getValue(Config.DefaultMaxProjectSnapshots.key())));
            this.projectResourceLimitMap.put(Resource.ResourceType.template, Long.parseLong(this._configDao.getValue(Config.DefaultMaxProjectTemplates.key())));
            this.projectResourceLimitMap.put(Resource.ResourceType.user_vm, Long.parseLong(this._configDao.getValue(Config.DefaultMaxProjectUserVms.key())));
            this.projectResourceLimitMap.put(Resource.ResourceType.volume, Long.parseLong(this._configDao.getValue(Config.DefaultMaxProjectVolumes.key())));
            this.projectResourceLimitMap.put(Resource.ResourceType.network, Long.parseLong(this._configDao.getValue(Config.DefaultMaxProjectNetworks.key())));
            this.projectResourceLimitMap.put(Resource.ResourceType.vpc, Long.parseLong(this._configDao.getValue(Config.DefaultMaxProjectVpcs.key())));
            this.projectResourceLimitMap.put(Resource.ResourceType.cpu, Long.parseLong(this._configDao.getValue(Config.DefaultMaxProjectCpus.key())));
            this.projectResourceLimitMap.put(Resource.ResourceType.memory, Long.parseLong(this._configDao.getValue(Config.DefaultMaxProjectMemory.key())));
            this.projectResourceLimitMap.put(Resource.ResourceType.primary_storage, Long.parseLong(this._configDao.getValue(Config.DefaultMaxProjectPrimaryStorage.key())));
            this.projectResourceLimitMap.put(Resource.ResourceType.secondary_storage, Long.parseLong(this._configDao.getValue(Config.DefaultMaxProjectSecondaryStorage.key())));

            this.accountResourceLimitMap.put(Resource.ResourceType.public_ip, Long.parseLong(this._configDao.getValue(Config.DefaultMaxAccountPublicIPs.key())));
            this.accountResourceLimitMap.put(Resource.ResourceType.snapshot, Long.parseLong(this._configDao.getValue(Config.DefaultMaxAccountSnapshots.key())));
            this.accountResourceLimitMap.put(Resource.ResourceType.template, Long.parseLong(this._configDao.getValue(Config.DefaultMaxAccountTemplates.key())));
            this.accountResourceLimitMap.put(Resource.ResourceType.user_vm, Long.parseLong(this._configDao.getValue(Config.DefaultMaxAccountUserVms.key())));
            this.accountResourceLimitMap.put(Resource.ResourceType.volume, Long.parseLong(this._configDao.getValue(Config.DefaultMaxAccountVolumes.key())));
            this.accountResourceLimitMap.put(Resource.ResourceType.network, Long.parseLong(this._configDao.getValue(Config.DefaultMaxAccountNetworks.key())));
            this.accountResourceLimitMap.put(Resource.ResourceType.vpc, Long.parseLong(this._configDao.getValue(Config.DefaultMaxAccountVpcs.key())));
            this.accountResourceLimitMap.put(Resource.ResourceType.cpu, Long.parseLong(this._configDao.getValue(Config.DefaultMaxAccountCpus.key())));
            this.accountResourceLimitMap.put(Resource.ResourceType.memory, Long.parseLong(this._configDao.getValue(Config.DefaultMaxAccountMemory.key())));
            this.accountResourceLimitMap.put(Resource.ResourceType.primary_storage, Long.parseLong(this._configDao.getValue(Config.DefaultMaxAccountPrimaryStorage.key())));
            this.accountResourceLimitMap.put(Resource.ResourceType.secondary_storage, Long.parseLong(this._configDao.getValue(Config.DefaultMaxAccountSecondaryStorage.key())));

            this.domainResourceLimitMap.put(Resource.ResourceType.public_ip, Long.parseLong(this._configDao.getValue(Config.DefaultMaxDomainPublicIPs.key())));
            this.domainResourceLimitMap.put(Resource.ResourceType.snapshot, Long.parseLong(this._configDao.getValue(Config.DefaultMaxDomainSnapshots.key())));
            this.domainResourceLimitMap.put(Resource.ResourceType.template, Long.parseLong(this._configDao.getValue(Config.DefaultMaxDomainTemplates.key())));
            this.domainResourceLimitMap.put(Resource.ResourceType.user_vm, Long.parseLong(this._configDao.getValue(Config.DefaultMaxDomainUserVms.key())));
            this.domainResourceLimitMap.put(Resource.ResourceType.volume, Long.parseLong(this._configDao.getValue(Config.DefaultMaxDomainVolumes.key())));
            this.domainResourceLimitMap.put(Resource.ResourceType.network, Long.parseLong(this._configDao.getValue(Config.DefaultMaxDomainNetworks.key())));
            this.domainResourceLimitMap.put(Resource.ResourceType.vpc, Long.parseLong(this._configDao.getValue(Config.DefaultMaxDomainVpcs.key())));
            this.domainResourceLimitMap.put(Resource.ResourceType.cpu, Long.parseLong(this._configDao.getValue(Config.DefaultMaxDomainCpus.key())));
            this.domainResourceLimitMap.put(Resource.ResourceType.memory, Long.parseLong(this._configDao.getValue(Config.DefaultMaxDomainMemory.key())));
            this.domainResourceLimitMap.put(Resource.ResourceType.primary_storage, Long.parseLong(this._configDao.getValue(Config.DefaultMaxDomainPrimaryStorage.key())));
            this.domainResourceLimitMap.put(Resource.ResourceType.secondary_storage, Long.parseLong(this._configDao.getValue(Config.DefaultMaxDomainSecondaryStorage.key())));
        } catch (final NumberFormatException e) {
            s_logger.error("NumberFormatException during configuration", e);
            throw new ConfigurationException("Configuration failed due to NumberFormatException, see log for the stacktrace");
        }

        return true;
    }

    @Override
    public boolean start() {
        if (this._resourceCountCheckInterval > 0) {
            this._rcExecutor.scheduleAtFixedRate(new ResourceCountCheckTask(), this._resourceCountCheckInterval, this._resourceCountCheckInterval, TimeUnit.SECONDS);
        }
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public ResourceLimitVO updateResourceLimit(final Long accountId, final Long domainId, final Integer typeId, Long max) {
        final Account caller = CallContext.current().getCallingAccount();

        if (max == null) {
            max = new Long(Resource.RESOURCE_UNLIMITED);
        } else if (max.longValue() < Resource.RESOURCE_UNLIMITED) {
            throw new InvalidParameterValueException("Please specify either '-1' for an infinite limit, or a limit that is at least '0'.");
        }

        // Map resource type
        ResourceType resourceType = null;
        if (typeId != null) {
            for (final ResourceType type : Resource.ResourceType.values()) {
                if (type.getOrdinal() == typeId.intValue()) {
                    resourceType = type;
                }
            }
            if (resourceType == null) {
                throw new InvalidParameterValueException("Please specify valid resource type");
            }
        }

        //Convert max storage size from GiB to bytes
        if ((resourceType == ResourceType.primary_storage || resourceType == ResourceType.secondary_storage) && max >= 0) {
            max = max * ResourceType.bytesToGiB;
        }

        ResourceOwnerType ownerType = null;
        Long ownerId = null;

        if (accountId != null) {
            final Account account = this._entityMgr.findById(Account.class, accountId);
            if (account == null) {
                throw new InvalidParameterValueException("Unable to find account " + accountId);
            }
            if (account.getId() == Account.ACCOUNT_ID_SYSTEM) {
                throw new InvalidParameterValueException("Can't update system account");
            }

            //only Unlimited value is accepted if account is  Root Admin
            if (this._accountMgr.isRootAdmin(account.getId()) && max.shortValue() != Resource.RESOURCE_UNLIMITED) {
                throw new InvalidParameterValueException("Only " + Resource.RESOURCE_UNLIMITED + " limit is supported for Root Admin accounts");
            }

            if ((caller.getAccountId() == accountId.longValue()) &&
                    (this._accountMgr.isDomainAdmin(caller.getId()) ||
                            caller.getType() == Account.ACCOUNT_TYPE_RESOURCE_DOMAIN_ADMIN)) {
                // If the admin is trying to update his own account, disallow.
                throw new PermissionDeniedException("Unable to update resource limit for his own account " + accountId + ", permission denied");
            }

            if (account.getType() == Account.ACCOUNT_TYPE_PROJECT) {
                this._accountMgr.checkAccess(caller, AccessType.ModifyProject, true, account);
            } else {
                this._accountMgr.checkAccess(caller, null, true, account);
            }

            ownerType = ResourceOwnerType.Account;
            ownerId = accountId;
        } else if (domainId != null) {
            final Domain domain = this._entityMgr.findById(Domain.class, domainId);

            this._accountMgr.checkAccess(caller, domain);

            if (Domain.ROOT_DOMAIN == domainId.longValue()) {
                // no one can add limits on ROOT domain, disallow...
                throw new PermissionDeniedException("Cannot update resource limit for ROOT domain " + domainId + ", permission denied");
            }

            if ((caller.getDomainId() == domainId.longValue()) && caller.getType() == Account.ACCOUNT_TYPE_DOMAIN_ADMIN ||
                    caller.getType() == Account.ACCOUNT_TYPE_RESOURCE_DOMAIN_ADMIN) {
                // if the admin is trying to update their own domain, disallow...
                throw new PermissionDeniedException("Unable to update resource limit for domain " + domainId + ", permission denied");
            }
            final Long parentDomainId = domain.getParent();
            if (parentDomainId != null) {
                final DomainVO parentDomain = this._domainDao.findById(parentDomainId);
                final long parentMaximum = findCorrectResourceLimitForDomain(parentDomain, resourceType);
                if ((parentMaximum >= 0) && (max.longValue() > parentMaximum)) {
                    throw new InvalidParameterValueException("Domain " + domain.getName() + "(id: " + parentDomain.getId() + ") has maximum allowed resource limit " +
                            parentMaximum + " for " + resourceType + ", please specify a value less that or equal to " + parentMaximum);
                }
            }
            ownerType = ResourceOwnerType.Domain;
            ownerId = domainId;
        }

        if (ownerId == null) {
            throw new InvalidParameterValueException("AccountId or domainId have to be specified in order to update resource limit");
        }

        final ResourceLimitVO limit = this._resourceLimitDao.findByOwnerIdAndType(ownerId, ownerType, resourceType);
        if (limit != null) {
            // Update the existing limit
            this._resourceLimitDao.update(limit.getId(), max);
            return this._resourceLimitDao.findById(limit.getId());
        } else {
            return this._resourceLimitDao.persist(new ResourceLimitVO(resourceType, max, ownerId, ownerType));
        }
    }

    @Override
    public List<ResourceCountVO> recalculateResourceCount(final Long accountId, final Long domainId, final Integer typeId) throws InvalidParameterValueException,
            CloudRuntimeException,
            PermissionDeniedException {
        final Account callerAccount = CallContext.current().getCallingAccount();
        long count;
        final List<ResourceCountVO> counts = new ArrayList<>();
        List<ResourceType> resourceTypes = new ArrayList<>();

        ResourceType resourceType = null;

        if (typeId != null) {
            for (final ResourceType type : Resource.ResourceType.values()) {
                if (type.getOrdinal() == typeId.intValue()) {
                    resourceType = type;
                }
            }
            if (resourceType == null) {
                throw new InvalidParameterValueException("Please specify valid resource type");
            }
        }

        final DomainVO domain = this._domainDao.findById(domainId);
        if (domain == null) {
            throw new InvalidParameterValueException("Please specify a valid domain ID.");
        }
        this._accountMgr.checkAccess(callerAccount, domain);

        if (resourceType != null) {
            resourceTypes.add(resourceType);
        } else {
            resourceTypes = Arrays.asList(Resource.ResourceType.values());
        }

        for (final ResourceType type : resourceTypes) {
            if (accountId != null) {
                if (type.supportsOwner(ResourceOwnerType.Account)) {
                    count = recalculateAccountResourceCount(accountId, type);
                    counts.add(new ResourceCountVO(type, count, accountId, ResourceOwnerType.Account));
                }
            } else {
                if (type.supportsOwner(ResourceOwnerType.Domain)) {
                    count = recalculateDomainResourceCount(domainId, type);
                    counts.add(new ResourceCountVO(type, count, domainId, ResourceOwnerType.Domain));
                }
            }
        }

        return counts;
    }

    @Override
    public List<ResourceLimitVO> searchForLimits(final Long id, Long accountId, Long domainId, final Integer type, final Long startIndex, final Long pageSizeVal) {
        final Account caller = CallContext.current().getCallingAccount();
        final List<ResourceLimitVO> limits = new ArrayList<>();
        final boolean isAccount;

        if (!this._accountMgr.isAdmin(caller.getId())) {
            accountId = caller.getId();
            domainId = null;
        } else {
            if (domainId != null) {
                // verify domain information and permissions
                final Domain domain = this._domainDao.findById(domainId);
                if (domain == null) {
                    // return empty set
                    return limits;
                }

                this._accountMgr.checkAccess(caller, domain);

                if (accountId != null) {
                    // Verify account information and permissions
                    final Account account = this._accountDao.findById(accountId);
                    if (account == null) {
                        // return empty set
                        return limits;
                    }

                    this._accountMgr.checkAccess(caller, null, true, account);
                    domainId = null;
                }
            }
        }

        // Map resource type
        ResourceType resourceType = null;
        if (type != null) {
            try {
                resourceType = ResourceType.values()[type];
            } catch (final ArrayIndexOutOfBoundsException e) {
                throw new InvalidParameterValueException("Please specify a valid resource type.");
            }
        }

        // If id is passed in, get the record and return it if permission check has passed
        if (id != null) {
            final ResourceLimitVO vo = this._resourceLimitDao.findById(id);
            if (vo.getAccountId() != null) {
                this._accountMgr.checkAccess(caller, null, true, this._accountDao.findById(vo.getAccountId()));
                limits.add(vo);
            } else if (vo.getDomainId() != null) {
                this._accountMgr.checkAccess(caller, this._domainDao.findById(vo.getDomainId()));
                limits.add(vo);
            }

            return limits;
        }

        // If account is not specified, default it to caller account
        if (accountId == null) {
            if (domainId == null) {
                accountId = caller.getId();
                isAccount = true;
            } else {
                isAccount = false;
            }
        } else {
            isAccount = true;
        }

        final SearchBuilder<ResourceLimitVO> sb = this._resourceLimitDao.createSearchBuilder();
        sb.and("accountId", sb.entity().getAccountId(), SearchCriteria.Op.EQ);
        sb.and("domainId", sb.entity().getDomainId(), SearchCriteria.Op.EQ);
        sb.and("type", sb.entity().getType(), SearchCriteria.Op.EQ);

        final SearchCriteria<ResourceLimitVO> sc = sb.create();
        final Filter filter = new Filter(ResourceLimitVO.class, "id", true, startIndex, pageSizeVal);

        if (accountId != null) {
            sc.setParameters("accountId", accountId);
        }

        if (domainId != null) {
            sc.setParameters("domainId", domainId);
            sc.setParameters("accountId", (Object[]) null);
        }

        if (resourceType != null) {
            sc.setParameters("type", resourceType);
        }

        final List<ResourceLimitVO> foundLimits = this._resourceLimitDao.search(sc, filter);

        if (resourceType != null) {
            if (foundLimits.isEmpty()) {
                if (isAccount) {
                    limits.add(new ResourceLimitVO(resourceType, findCorrectResourceLimitForAccount(this._accountMgr.getAccount(accountId), resourceType), accountId,
                            ResourceOwnerType.Account));
                } else {
                    limits.add(new ResourceLimitVO(resourceType, findCorrectResourceLimitForDomain(this._domainDao.findById(domainId), resourceType), domainId,
                            ResourceOwnerType.Domain));
                }
            } else {
                limits.addAll(foundLimits);
            }
        } else {
            limits.addAll(foundLimits);

            // see if any limits are missing from the table, and if yes - get it from the config table and add
            final ResourceType[] resourceTypes = ResourceCount.ResourceType.values();
            if (foundLimits.size() != resourceTypes.length) {
                final List<String> accountLimitStr = new ArrayList<>();
                final List<String> domainLimitStr = new ArrayList<>();
                for (final ResourceLimitVO foundLimit : foundLimits) {
                    if (foundLimit.getAccountId() != null) {
                        accountLimitStr.add(foundLimit.getType().toString());
                    } else {
                        domainLimitStr.add(foundLimit.getType().toString());
                    }
                }

                // get default from config values
                if (isAccount) {
                    if (accountLimitStr.size() < resourceTypes.length) {
                        for (final ResourceType rt : resourceTypes) {
                            if (!accountLimitStr.contains(rt.toString()) && rt.supportsOwner(ResourceOwnerType.Account)) {
                                limits.add(new ResourceLimitVO(rt, findCorrectResourceLimitForAccount(this._accountMgr.getAccount(accountId), rt), accountId,
                                        ResourceOwnerType.Account));
                            }
                        }
                    }
                } else {
                    if (domainLimitStr.size() < resourceTypes.length) {
                        for (final ResourceType rt : resourceTypes) {
                            if (!domainLimitStr.contains(rt.toString()) && rt.supportsOwner(ResourceOwnerType.Domain)) {
                                limits.add(new ResourceLimitVO(rt, findCorrectResourceLimitForDomain(this._domainDao.findById(domainId), rt), domainId,
                                        ResourceOwnerType.Domain));
                            }
                        }
                    }
                }
            }
        }

        return limits;
    }

    @Override
    public long findCorrectResourceLimitForAccount(final Account account, final ResourceType type) {

        long max = Resource.RESOURCE_UNLIMITED; // if resource limit is not found, then we treat it as unlimited

        // No limits for Root Admin accounts
        if (this._accountMgr.isRootAdmin(account.getId())) {
            return max;
        }

        final ResourceLimitVO limit = this._resourceLimitDao.findByOwnerIdAndType(account.getId(), ResourceOwnerType.Account, type);

        // Check if limit is configured for account
        if (limit != null) {
            max = limit.getMax().longValue();
        } else {
            // If the account has an no limit set, then return global default account limits
            Long value;
            if (account.getType() == Account.ACCOUNT_TYPE_PROJECT) {
                value = this.projectResourceLimitMap.get(type);
            } else {
                value = this.accountResourceLimitMap.get(type);
            }
            if (value != null) {
                if (value < 0) { // return unlimit if value is set to negative
                    return max;
                }
                // convert the value from GiB to bytes in case of primary or secondary storage.
                if (type == ResourceType.primary_storage || type == ResourceType.secondary_storage) {
                    value = value * ResourceType.bytesToGiB;
                }
                return value;
            }
        }

        return max;
    }

    @Override
    public long findCorrectResourceLimitForAccount(final long accountId, final Long limit, final ResourceType type) {

        long max = Resource.RESOURCE_UNLIMITED; // if resource limit is not found, then we treat it as unlimited

        // No limits for Root Admin accounts
        if (this._accountMgr.isRootAdmin(accountId)) {
            return max;
        }

        final Account account = this._accountDao.findById(accountId);
        if (account == null) {
            return max;
        }

        // Check if limit is configured for account
        if (limit != null) {
            max = limit.longValue();
        } else {
            // If the account has an no limit set, then return global default account limits
            Long value;
            if (account.getType() == Account.ACCOUNT_TYPE_PROJECT) {
                value = this.projectResourceLimitMap.get(type);
            } else {
                value = this.accountResourceLimitMap.get(type);
            }
            if (value != null) {
                if (value < 0) { // return unlimit if value is set to negative
                    return max;
                }
                if (type == ResourceType.primary_storage || type == ResourceType.secondary_storage) {
                    value = value * ResourceType.bytesToGiB;
                }
                return value;
            }
        }

        return max;
    }

    @Override
    public long findCorrectResourceLimitForDomain(final Domain domain, final ResourceType type) {
        long max = Resource.RESOURCE_UNLIMITED;

        // no limits on ROOT domain
        if (domain.getId() == Domain.ROOT_DOMAIN) {
            return Resource.RESOURCE_UNLIMITED;
        }
        // Check account
        ResourceLimitVO limit = this._resourceLimitDao.findByOwnerIdAndType(domain.getId(), ResourceOwnerType.Domain, type);

        if (limit != null) {
            max = limit.getMax().longValue();
        } else {
            // check domain hierarchy
            Long domainId = domain.getParent();
            while ((domainId != null) && (limit == null)) {
                if (domainId == Domain.ROOT_DOMAIN) {
                    break;
                }
                limit = this._resourceLimitDao.findByOwnerIdAndType(domainId, ResourceOwnerType.Domain, type);
                final DomainVO tmpDomain = this._domainDao.findById(domainId);
                domainId = tmpDomain.getParent();
            }

            if (limit != null) {
                max = limit.getMax().longValue();
            } else {
                Long value;
                value = this.domainResourceLimitMap.get(type);
                if (value != null) {
                    if (value < 0) { // return unlimit if value is set to negative
                        return max;
                    }
                    if (type == ResourceType.primary_storage || type == ResourceType.secondary_storage) {
                        value = value * ResourceType.bytesToGiB;
                    }
                    return value;
                }
            }
        }

        return max;
    }

    @Override
    public void incrementResourceCount(final long accountId, final ResourceType type, final Long... delta) {
        // don't upgrade resource count for system account
        if (accountId == Account.ACCOUNT_ID_SYSTEM) {
            s_logger.trace("Not incrementing resource count for system accounts, returning");
            return;
        }

        final long numToIncrement = (delta.length == 0) ? 1 : delta[0].longValue();

        if (!updateResourceCountForAccount(accountId, type, true, numToIncrement)) {
            // we should fail the operation (resource creation) when failed to update the resource count
            throw new CloudRuntimeException("Failed to increment resource count of type " + type + " for account id=" + accountId);
        }
    }

    @Override
    public void decrementResourceCount(final long accountId, final ResourceType type, final Long... delta) {
        // don't upgrade resource count for system account
        if (accountId == Account.ACCOUNT_ID_SYSTEM) {
            s_logger.trace("Not decrementing resource count for system accounts, returning");
            return;
        }
        final long numToDecrement = (delta.length == 0) ? 1 : delta[0].longValue();

        if (!updateResourceCountForAccount(accountId, type, false, numToDecrement)) {
            this._alertMgr.sendAlert(AlertManager.AlertType.ALERT_TYPE_UPDATE_RESOURCE_COUNT, 0L, 0L, "Failed to decrement resource count of type " + type +
                    " for account id=" +
                    accountId, "Failed to decrement resource count of type " + type + " for account id=" + accountId +
                    "; use updateResourceCount API to recalculate/fix the problem");
        }
    }

    @Override
    @DB
    public void checkResourceLimit(final Account account, final ResourceType type, final long... count) throws ResourceAllocationException {
        final long numResources = ((count.length == 0) ? 1 : count[0]);
        Project project = null;

        // Don't place any limits on system or root admin accounts
        if (this._accountMgr.isRootAdmin(account.getId())) {
            return;
        }

        if (account.getType() == Account.ACCOUNT_TYPE_PROJECT) {
            project = this._projectDao.findByProjectAccountId(account.getId());
        }

        final Project projectFinal = project;
        Transaction.execute(new TransactionCallbackWithExceptionNoReturn<ResourceAllocationException>() {
            @Override
            public void doInTransactionWithoutResult(final TransactionStatus status) throws ResourceAllocationException {
                // Lock all rows first so nobody else can read it
                final Set<Long> rowIdsToLock = ResourceLimitManagerImpl.this._resourceCountDao.listAllRowsToUpdate(account.getId(), ResourceOwnerType.Account, type);
                final SearchCriteria<ResourceCountVO> sc = ResourceLimitManagerImpl.this.ResourceCountSearch.create();
                sc.setParameters("id", rowIdsToLock.toArray());
                ResourceLimitManagerImpl.this._resourceCountDao.lockRows(sc, null, true);

                // Check account limits
                final long accountLimit = findCorrectResourceLimitForAccount(account, type);
                final long potentialCount = ResourceLimitManagerImpl.this._resourceCountDao.getResourceCount(account.getId(), ResourceOwnerType.Account, type) + numResources;
                if (accountLimit != Resource.RESOURCE_UNLIMITED && potentialCount > accountLimit) {
                    String message =
                            "Maximum number of resources of type '" + type + "' for account name=" + account.getAccountName() + " in domain id=" + account.getDomainId() +
                                    " has been exceeded.";
                    if (projectFinal != null) {
                        message =
                                "Maximum number of resources of type '" + type + "' for project name=" + projectFinal.getName() + " in domain id=" + account.getDomainId() +
                                        " has been exceeded.";
                    }
                    final ResourceAllocationException e = new ResourceAllocationException(message, type);
                    s_logger.error(message, e);
                    throw e;
                }

                // check all domains in the account's domain hierarchy
                Long domainId;
                if (projectFinal != null) {
                    domainId = projectFinal.getDomainId();
                } else {
                    domainId = account.getDomainId();
                }

                while (domainId != null) {
                    final DomainVO domain = ResourceLimitManagerImpl.this._domainDao.findById(domainId);
                    // no limit check if it is ROOT domain
                    if (domainId != Domain.ROOT_DOMAIN) {
                        final long domainLimit = findCorrectResourceLimitForDomain(domain, type);
                        final long domainCount = ResourceLimitManagerImpl.this._resourceCountDao.getResourceCount(domainId, ResourceOwnerType.Domain, type) + numResources;
                        if (domainLimit != Resource.RESOURCE_UNLIMITED && domainCount > domainLimit) {
                            throw new ResourceAllocationException("Maximum number of resources of type '" + type + "' for domain id=" + domainId + " has been exceeded.", type);
                        }
                    }
                    domainId = domain.getParent();
                }
            }
        });
    }

    @Override
    public long getResourceCount(final Account account, final ResourceType type) {
        return this._resourceCountDao.getResourceCount(account.getId(), ResourceOwnerType.Account, type);
    }

    @Override
    public void checkResourceLimit(final Account account, final ResourceType type, final Boolean displayResource, final long... count) throws ResourceAllocationException {

        if (isDisplayFlagOn(displayResource)) {
            checkResourceLimit(account, type, count);
        }
    }

    private boolean isDisplayFlagOn(final Boolean displayResource) {

        // 1. If its null assume displayResource = 1
        // 2. If its not null then send true if displayResource = 1
        return (displayResource == null) || (displayResource != null && displayResource);
    }

    @Override
    public void incrementResourceCount(final long accountId, final ResourceType type, final Boolean displayResource, final Long... delta) {

        if (isDisplayFlagOn(displayResource)) {
            incrementResourceCount(accountId, type, delta);
        }
    }

    @DB
    protected boolean updateResourceCountForAccount(final long accountId, final ResourceType type, final boolean increment, final long delta) {
        try {
            return Transaction.execute(new TransactionCallback<Boolean>() {
                @Override
                public Boolean doInTransaction(final TransactionStatus status) {
                    boolean result = true;
                    final Set<Long> rowsToLock = ResourceLimitManagerImpl.this._resourceCountDao.listAllRowsToUpdate(accountId, ResourceOwnerType.Account, type);

                    // Lock rows first
                    final SearchCriteria<ResourceCountVO> sc = ResourceLimitManagerImpl.this.ResourceCountSearch.create();
                    sc.setParameters("id", rowsToLock.toArray());
                    final List<ResourceCountVO> rowsToUpdate = ResourceLimitManagerImpl.this._resourceCountDao.lockRows(sc, null, true);

                    for (final ResourceCountVO rowToUpdate : rowsToUpdate) {
                        if (!ResourceLimitManagerImpl.this._resourceCountDao.updateById(rowToUpdate.getId(), increment, delta)) {
                            s_logger.trace("Unable to update resource count for the row " + rowToUpdate);
                            result = false;
                        }
                    }

                    return result;
                }
            });
        } catch (final Exception ex) {
            s_logger.error("Failed to update resource count for account id=" + accountId);
            return false;
        }
    }

    @Override
    public void changeResourceCount(final long accountId, final ResourceType type, final Boolean displayResource, final Long... delta) {

        // meaning that the display flag is not changed so neither increment or decrement
        if (displayResource == null) {
            return;
        }

        // Increment because the display is turned on.
        if (displayResource) {
            incrementResourceCount(accountId, type, delta);
        } else {
            decrementResourceCount(accountId, type, delta);
        }
    }

    @Override
    public void decrementResourceCount(final long accountId, final ResourceType type, final Boolean displayResource, final Long... delta) {

        if (isDisplayFlagOn(displayResource)) {
            decrementResourceCount(accountId, type, delta);
        }
    }

    @DB
    protected long recalculateDomainResourceCount(final long domainId, final ResourceType type) {
        return Transaction.execute(new TransactionCallback<Long>() {
            @Override
            public Long doInTransaction(final TransactionStatus status) {
                long newCount = 0;

                // Lock all rows first so nobody else can read it
                final Set<Long> rowIdsToLock = ResourceLimitManagerImpl.this._resourceCountDao.listAllRowsToUpdate(domainId, ResourceOwnerType.Domain, type);
                final SearchCriteria<ResourceCountVO> sc = ResourceLimitManagerImpl.this.ResourceCountSearch.create();
                sc.setParameters("id", rowIdsToLock.toArray());
                ResourceLimitManagerImpl.this._resourceCountDao.lockRows(sc, null, true);

                final ResourceCountVO domainRC = ResourceLimitManagerImpl.this._resourceCountDao.findByOwnerAndType(domainId, ResourceOwnerType.Domain, type);
                final long oldCount = domainRC.getCount();

                final List<DomainVO> domainChildren = ResourceLimitManagerImpl.this._domainDao.findImmediateChildrenForParent(domainId);
                // for each child domain update the resource count
                if (type.supportsOwner(ResourceOwnerType.Domain)) {

                    // calculate project count here
                    if (type == ResourceType.project) {
                        newCount = newCount + ResourceLimitManagerImpl.this._projectDao.countProjectsForDomain(domainId);
                    }

                    for (final DomainVO domainChild : domainChildren) {
                        final long domainCount = recalculateDomainResourceCount(domainChild.getId(), type);
                        newCount = newCount + domainCount; // add the child domain count to parent domain count
                    }
                }

                if (type.supportsOwner(ResourceOwnerType.Account)) {
                    final List<AccountVO> accounts = ResourceLimitManagerImpl.this._accountDao.findActiveAccountsForDomain(domainId);
                    for (final AccountVO account : accounts) {
                        final long accountCount = recalculateAccountResourceCount(account.getId(), type);
                        newCount = newCount + accountCount; // add account's resource count to parent domain count
                    }
                }
                ResourceLimitManagerImpl.this._resourceCountDao.setResourceCount(domainId, ResourceOwnerType.Domain, type, newCount);

                if (oldCount != newCount) {
                    s_logger.info("Discrepency in the resource count " + "(original count=" + oldCount + " correct count = " + newCount + ") for type " + type +
                            " for domain ID " + domainId + " is fixed during resource count recalculation.");
                }

                return newCount;
            }
        });
    }

    @DB
    protected long recalculateAccountResourceCount(final long accountId, final ResourceType type) {
        final Long newCount = Transaction.execute(new TransactionCallback<Long>() {
            @Override
            public Long doInTransaction(final TransactionStatus status) {
                Long newCount;

                // this lock guards against the updates to user_vm, volume, snapshot, public _ip and template table
                // as any resource creation precedes with the resourceLimitExceeded check which needs this lock too
                final Set rowIdsToLock = ResourceLimitManagerImpl.this._resourceCountDao.listAllRowsToUpdate(accountId, Resource.ResourceOwnerType.Account, type);
                final SearchCriteria<ResourceCountVO> sc = ResourceLimitManagerImpl.this.ResourceCountSearch.create();
                sc.setParameters("id", rowIdsToLock.toArray());
                ResourceLimitManagerImpl.this._resourceCountDao.lockRows(sc, null, true);

                final ResourceCountVO accountRC = ResourceLimitManagerImpl.this._resourceCountDao.findByOwnerAndType(accountId, ResourceOwnerType.Account, type);
                long oldCount = 0;
                if (accountRC != null) {
                    oldCount = accountRC.getCount();
                }

                if (type == Resource.ResourceType.user_vm) {
                    newCount = ResourceLimitManagerImpl.this._userVmDao.countAllocatedVMsForAccount(accountId);
                } else if (type == Resource.ResourceType.volume) {
                    newCount = ResourceLimitManagerImpl.this._volumeDao.countAllocatedVolumesForAccount(accountId);
                    final long virtualRouterCount = ResourceLimitManagerImpl.this._vmDao.findIdsOfAllocatedVirtualRoutersForAccount(accountId).size();
                    newCount = newCount - virtualRouterCount; // don't count the volumes of virtual router
                } else if (type == Resource.ResourceType.snapshot) {
                    newCount = ResourceLimitManagerImpl.this._snapshotDao.countSnapshotsForAccount(accountId);
                } else if (type == Resource.ResourceType.public_ip) {
                    newCount = calculatePublicIpForAccount(accountId);
                } else if (type == Resource.ResourceType.template) {
                    newCount = ResourceLimitManagerImpl.this._vmTemplateDao.countTemplatesForAccount(accountId);
                } else if (type == Resource.ResourceType.project) {
                    newCount = ResourceLimitManagerImpl.this._projectAccountDao.countByAccountIdAndRole(accountId, Role.Admin);
                } else if (type == Resource.ResourceType.network) {
                    newCount = ResourceLimitManagerImpl.this._networkDao.countNetworksUserCanCreate(accountId);
                } else if (type == Resource.ResourceType.vpc) {
                    newCount = ResourceLimitManagerImpl.this._vpcDao.countByAccountId(accountId);
                } else if (type == Resource.ResourceType.cpu) {
                    newCount = countCpusForAccount(accountId);
                } else if (type == Resource.ResourceType.memory) {
                    newCount = calculateMemoryForAccount(accountId);
                } else if (type == Resource.ResourceType.primary_storage) {
                    final List<Long> virtualRouters = ResourceLimitManagerImpl.this._vmDao.findIdsOfAllocatedVirtualRoutersForAccount(accountId);
                    newCount = ResourceLimitManagerImpl.this._volumeDao.primaryStorageUsedForAccount(accountId, virtualRouters);
                } else if (type == Resource.ResourceType.secondary_storage) {
                    newCount = calculateSecondaryStorageForAccount(accountId);
                } else {
                    throw new InvalidParameterValueException("Unsupported resource type " + type);
                }
                ResourceLimitManagerImpl.this._resourceCountDao.setResourceCount(accountId, ResourceOwnerType.Account, type, (newCount == null) ? 0 : newCount.longValue());

                // No need to log message for primary and secondary storage because both are recalculating the resource count which will not lead to any discrepancy.
                if (!Long.valueOf(oldCount).equals(newCount) && (type != Resource.ResourceType.primary_storage && type != Resource.ResourceType.secondary_storage)) {
                    s_logger.info("Discrepency in the resource count " + "(original count=" + oldCount + " correct count = " + newCount + ") for type " + type +
                            " for account ID " + accountId + " is fixed during resource count recalculation.");
                }

                return newCount;
            }
        });

        return (newCount == null) ? 0 : newCount.longValue();
    }

    public long countCpusForAccount(final long accountId) {
        final GenericSearchBuilder<ServiceOfferingVO, SumCount> cpuSearch = this._serviceOfferingDao.createSearchBuilder(SumCount.class);
        cpuSearch.select("sum", Func.SUM, cpuSearch.entity().getCpu());
        final SearchBuilder<UserVmVO> join1 = this._userVmDao.createSearchBuilder();
        join1.and("accountId", join1.entity().getAccountId(), Op.EQ);
        join1.and("type", join1.entity().getType(), Op.EQ);
        join1.and("state", join1.entity().getState(), SearchCriteria.Op.NIN);
        join1.and("displayVm", join1.entity().isDisplayVm(), Op.EQ);
        cpuSearch.join("offerings", join1, cpuSearch.entity().getId(), join1.entity().getServiceOfferingId(), JoinBuilder.JoinType.INNER);
        cpuSearch.done();

        final SearchCriteria<SumCount> sc = cpuSearch.create();
        sc.setJoinParameters("offerings", "accountId", accountId);
        sc.setJoinParameters("offerings", "type", VirtualMachineType.User);
        sc.setJoinParameters("offerings", "state", new Object[]{State.Destroyed, State.Error, State.Expunging});
        sc.setJoinParameters("offerings", "displayVm", 1);
        final List<SumCount> cpus = this._serviceOfferingDao.customSearch(sc, null);
        if (cpus != null) {
            return cpus.get(0).sum;
        } else {
            return 0;
        }
    }

    public long calculateMemoryForAccount(final long accountId) {
        final GenericSearchBuilder<ServiceOfferingVO, SumCount> memorySearch = this._serviceOfferingDao.createSearchBuilder(SumCount.class);
        memorySearch.select("sum", Func.SUM, memorySearch.entity().getRamSize());
        final SearchBuilder<UserVmVO> join1 = this._userVmDao.createSearchBuilder();
        join1.and("accountId", join1.entity().getAccountId(), Op.EQ);
        join1.and("type", join1.entity().getType(), Op.EQ);
        join1.and("state", join1.entity().getState(), SearchCriteria.Op.NIN);
        join1.and("displayVm", join1.entity().isDisplayVm(), Op.EQ);
        memorySearch.join("offerings", join1, memorySearch.entity().getId(), join1.entity().getServiceOfferingId(), JoinBuilder.JoinType.INNER);
        memorySearch.done();

        final SearchCriteria<SumCount> sc = memorySearch.create();
        sc.setJoinParameters("offerings", "accountId", accountId);
        sc.setJoinParameters("offerings", "type", VirtualMachineType.User);
        sc.setJoinParameters("offerings", "state", new Object[]{State.Destroyed, State.Error, State.Expunging});
        sc.setJoinParameters("offerings", "displayVm", 1);
        final List<SumCount> memory = this._serviceOfferingDao.customSearch(sc, null);
        if (memory != null) {
            return memory.get(0).sum;
        } else {
            return 0;
        }
    }

    public long calculateSecondaryStorageForAccount(final long accountId) {
        final long totalVolumesSize = this._volumeDao.secondaryStorageUsedForAccount(accountId);
        long totalSnapshotsSize = 0;
        long totalTemplatesSize = 0;

        final SearchCriteria<SumCount> sc = this.templateSizeSearch.create();
        sc.setParameters("downloadState", VMTemplateStatus.DOWNLOADED);
        sc.setParameters("destroyed", false);
        sc.setJoinParameters("templates", "accountId", accountId);
        final List<SumCount> templates = this._vmTemplateStoreDao.customSearch(sc, null);
        if (templates != null) {
            totalTemplatesSize = templates.get(0).sum;
        }

        final SearchCriteria<SumCount> sc2 = this.snapshotSizeSearch.create();
        sc2.setParameters("state", ObjectInDataStoreStateMachine.State.Ready);
        sc2.setParameters("storeRole", DataStoreRole.Image);
        sc2.setJoinParameters("snapshots", "accountId", accountId);
        final List<SumCount> snapshots = this._snapshotDataStoreDao.customSearch(sc2, null);
        if (snapshots != null) {
            totalSnapshotsSize = snapshots.get(0).sum;
        }
        return totalVolumesSize + totalSnapshotsSize + totalTemplatesSize;
    }

    private long calculatePublicIpForAccount(final long accountId) {
        Long dedicatedCount = 0L;
        final Long allocatedCount;

        final List<VlanVO> dedicatedVlans = this._vlanDao.listDedicatedVlans(accountId);
        for (final VlanVO dedicatedVlan : dedicatedVlans) {
            final List<IPAddressVO> ips = this._ipAddressDao.listByVlanId(dedicatedVlan.getId());
            dedicatedCount += new Long(ips.size());
        }
        allocatedCount = this._ipAddressDao.countAllocatedIPsForAccount(accountId);
        if (dedicatedCount > allocatedCount) {
            return dedicatedCount;
        } else {
            return allocatedCount;
        }
    }

    protected class ResourceCountCheckTask extends ManagedContextRunnable {
        public ResourceCountCheckTask() {

        }

        @Override
        protected void runInContext() {
            s_logger.info("Running resource count check periodic task");
            final List<DomainVO> domains = ResourceLimitManagerImpl.this._domainDao.findImmediateChildrenForParent(Domain.ROOT_DOMAIN);

            // recalculateDomainResourceCount will take care of re-calculation of resource counts for sub-domains
            // and accounts of the sub-domains also. so just loop through immediate children of root domain
            for (final Domain domain : domains) {
                for (final ResourceType type : ResourceCount.ResourceType.values()) {
                    if (type.supportsOwner(ResourceOwnerType.Domain)) {
                        recalculateDomainResourceCount(domain.getId(), type);
                    }
                }
            }

            // run through the accounts in the root domain
            final List<AccountVO> accounts = ResourceLimitManagerImpl.this._accountDao.findActiveAccountsForDomain(Domain.ROOT_DOMAIN);
            for (final AccountVO account : accounts) {
                for (final ResourceType type : ResourceCount.ResourceType.values()) {
                    if (type.supportsOwner(ResourceOwnerType.Account)) {
                        recalculateAccountResourceCount(account.getId(), type);
                    }
                }
            }
        }
    }
}
