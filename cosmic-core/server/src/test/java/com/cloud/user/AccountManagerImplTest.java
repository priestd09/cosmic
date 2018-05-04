package com.cloud.user;

import com.cloud.acl.SecurityChecker;
import com.cloud.acl.SecurityChecker.AccessType;
import com.cloud.affinity.dao.AffinityGroupDao;
import com.cloud.configuration.ConfigurationManager;
import com.cloud.configuration.dao.ResourceCountDao;
import com.cloud.configuration.dao.ResourceLimitDao;
import com.cloud.context.CallContext;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.DataCenterVnetDao;
import com.cloud.dc.dao.DedicatedResourceDao;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.engine.orchestration.service.NetworkOrchestrationService;
import com.cloud.framework.config.dao.ConfigurationDao;
import com.cloud.framework.messagebus.MessageBus;
import com.cloud.legacymodel.acl.ControlledEntity;
import com.cloud.legacymodel.domain.Domain;
import com.cloud.legacymodel.exceptions.ConcurrentOperationException;
import com.cloud.legacymodel.exceptions.ResourceUnavailableException;
import com.cloud.legacymodel.user.Account;
import com.cloud.legacymodel.user.Account.State;
import com.cloud.legacymodel.user.User;
import com.cloud.legacymodel.user.UserAccount;
import com.cloud.legacymodel.utils.Pair;
import com.cloud.network.dao.AccountGuestVlanMapDao;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.RemoteAccessVpnDao;
import com.cloud.network.dao.VpnUserDao;
import com.cloud.network.vpc.VpcManager;
import com.cloud.network.vpn.RemoteAccessVpnService;
import com.cloud.network.vpn.Site2SiteVpnManager;
import com.cloud.projects.ProjectManager;
import com.cloud.projects.dao.ProjectAccountDao;
import com.cloud.projects.dao.ProjectDao;
import com.cloud.server.auth.UserAuthenticator;
import com.cloud.storage.VolumeApiService;
import com.cloud.storage.dao.SnapshotDao;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.storage.snapshot.SnapshotManager;
import com.cloud.template.TemplateManager;
import com.cloud.user.dao.AccountDao;
import com.cloud.user.dao.UserAccountDao;
import com.cloud.user.dao.UserDao;
import com.cloud.vm.UserVmManager;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.dao.DomainRouterDao;
import com.cloud.vm.dao.InstanceGroupDao;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.VMInstanceDao;
import com.cloud.vm.snapshot.VMSnapshotManager;
import com.cloud.vm.snapshot.dao.VMSnapshotDao;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class AccountManagerImplTest {
    @Mock
    protected SnapshotDao _snapshotDao;
    @Mock
    protected VMTemplateDao _vmTemplateDao;
    @Mock
    AccountDao _accountDao;
    @Mock
    ConfigurationDao _configDao;
    @Mock
    ResourceCountDao _resourceCountDao;
    @Mock
    UserDao _userDao;
    @Mock
    InstanceGroupDao _vmGroupDao;
    @Mock
    UserAccountDao _userAccountDao;
    @Mock
    VolumeDao _volumeDao;
    @Mock
    UserVmDao _userVmDao;
    @Mock
    VMTemplateDao _templateDao;
    @Mock
    NetworkDao _networkDao;
    @Mock
    VMInstanceDao _vmDao;
    @Mock
    NetworkOrchestrationService _networkMgr;
    @Mock
    SnapshotManager _snapMgr;
    @Mock
    UserVmManager _vmMgr;
    @Mock
    TemplateManager _tmpltMgr;
    @Mock
    ConfigurationManager _configMgr;
    @Mock
    VirtualMachineManager _itMgr;
    @Mock
    RemoteAccessVpnDao _remoteAccessVpnDao;
    @Mock
    RemoteAccessVpnService _remoteAccessVpnMgr;
    @Mock
    VpnUserDao _vpnUser;
    @Mock
    DataCenterDao _dcDao;
    @Mock
    DomainManager _domainMgr;
    @Mock
    ProjectManager _projectMgr;
    @Mock
    ProjectDao _projectDao;
    @Mock
    AccountDetailsDao _accountDetailsDao;
    @Mock
    DomainDao _domainDao;
    @Mock
    ProjectAccountDao _projectAccountDao;
    @Mock
    IPAddressDao _ipAddressDao;
    @Mock
    VpcManager _vpcMgr;
    @Mock
    DomainRouterDao _routerDao;
    @Mock
    Site2SiteVpnManager _vpnMgr;
    @Mock
    VolumeApiService volumeService;
    @Mock
    AffinityGroupDao _affinityGroupDao;
    @Mock
    AccountGuestVlanMapDao _accountGuestVlanMapDao;
    @Mock
    DataCenterVnetDao _dataCenterVnetDao;
    @Mock
    ResourceLimitService _resourceLimitMgr;
    @Mock
    ResourceLimitDao _resourceLimitDao;
    @Mock
    DedicatedResourceDao _dedicatedDao;
    @Mock
    MessageBus _messageBus;

    @Mock
    VMSnapshotManager _vmSnapshotMgr;
    @Mock
    VMSnapshotDao _vmSnapshotDao;

    @Mock
    User callingUser;
    @Mock
    Account callingAccount;

    AccountManagerImpl accountManager;

    @Mock
    SecurityChecker securityChecker;

    @Mock
    private UserAuthenticator userAuthenticator;

    @Before
    public void setup() throws NoSuchFieldException, SecurityException,
            IllegalArgumentException, IllegalAccessException {
        accountManager = new AccountManagerImpl();
        for (final Field field : AccountManagerImpl.class.getDeclaredFields()) {
            if (field.getAnnotation(Inject.class) != null) {
                field.setAccessible(true);
                try {
                    final Field mockField = this.getClass().getDeclaredField(
                            field.getName());
                    field.set(accountManager, mockField.get(this));
                } catch (final Exception e) {
                    // ignore missing fields
                }
            }
        }
        ReflectionTestUtils.setField(accountManager, "_userAuthenticators", Arrays.asList(userAuthenticator));
        accountManager.setSecurityCheckers(Arrays.asList(securityChecker));
        CallContext.register(callingUser, callingAccount);
    }

    @After
    public void cleanup() {
        CallContext.unregister();
    }

    @Test
    public void disableAccountNotexisting()
            throws ConcurrentOperationException, ResourceUnavailableException {
        Mockito.when(_accountDao.findById(42l)).thenReturn(null);
        Assert.assertTrue(accountManager.disableAccount(42));
    }

    @Test
    public void disableAccountDisabled() throws ConcurrentOperationException,
            ResourceUnavailableException {
        final AccountVO disabledAccount = new AccountVO();
        disabledAccount.setState(State.disabled);
        Mockito.when(_accountDao.findById(42l)).thenReturn(disabledAccount);
        Assert.assertTrue(accountManager.disableAccount(42));
    }

    @Test
    public void disableAccount() throws ConcurrentOperationException,
            ResourceUnavailableException {
        final AccountVO account = new AccountVO();
        account.setState(State.enabled);
        Mockito.when(_accountDao.findById(42l)).thenReturn(account);
        Mockito.when(_accountDao.createForUpdate()).thenReturn(new AccountVO());
        Mockito.when(
                _accountDao.update(Mockito.eq(42l),
                        Mockito.any(AccountVO.class))).thenReturn(true);
        Mockito.when(_vmDao.listByAccountId(42l)).thenReturn(
                Arrays.asList(Mockito.mock(VMInstanceVO.class)));
        Assert.assertTrue(accountManager.disableAccount(42));
        Mockito.verify(_accountDao, Mockito.atLeastOnce()).update(
                Mockito.eq(42l), Mockito.any(AccountVO.class));
    }

    @Test
    public void deleteUserAccount() {
        final AccountVO account = new AccountVO();
        account.setId(42l);
        final DomainVO domain = new DomainVO();
        Mockito.when(_accountDao.findById(42l)).thenReturn(account);
        Mockito.when(
                securityChecker.checkAccess(Mockito.any(Account.class),
                        Mockito.any(ControlledEntity.class), Mockito.any(AccessType.class),
                        Mockito.anyString()))
               .thenReturn(true);
        Mockito.when(_accountDao.remove(42l)).thenReturn(true);
        Mockito.when(_configMgr.releaseAccountSpecificVirtualRanges(42l))
               .thenReturn(true);
        Mockito.when(_domainMgr.getDomain(Mockito.anyLong())).thenReturn(domain);
        Mockito.when(
                securityChecker.checkAccess(Mockito.any(Account.class),
                        Mockito.any(Domain.class)))
               .thenReturn(true);
        Mockito.when(_vmSnapshotDao.listByAccountId(Mockito.anyLong())).thenReturn(new ArrayList<>());

        Assert.assertTrue(accountManager.deleteUserAccount(42));
        // assert that this was a clean delete
        Mockito.verify(_accountDao, Mockito.never()).markForCleanup(
                Mockito.eq(42l));
    }

    @Test
    public void deleteUserAccountCleanup() {
        final AccountVO account = new AccountVO();
        account.setId(42l);
        final DomainVO domain = new DomainVO();
        Mockito.when(_accountDao.findById(42l)).thenReturn(account);
        Mockito.when(
                securityChecker.checkAccess(Mockito.any(Account.class),
                        Mockito.any(ControlledEntity.class), Mockito.any(AccessType.class),
                        Mockito.anyString()))
               .thenReturn(true);
        Mockito.when(_accountDao.remove(42l)).thenReturn(true);
        Mockito.when(_configMgr.releaseAccountSpecificVirtualRanges(42l))
               .thenReturn(true);
        Mockito.when(_userVmDao.listByAccountId(42l)).thenReturn(
                Arrays.asList(Mockito.mock(UserVmVO.class)));
        Mockito.when(
                _vmMgr.expunge(Mockito.any(UserVmVO.class), Mockito.anyLong(),
                        Mockito.any(Account.class))).thenReturn(false);
        Mockito.when(_domainMgr.getDomain(Mockito.anyLong())).thenReturn(domain);
        Mockito.when(
                securityChecker.checkAccess(Mockito.any(Account.class),
                        Mockito.any(Domain.class)))
               .thenReturn(true);

        Assert.assertTrue(accountManager.deleteUserAccount(42));
        // assert that this was NOT a clean delete
        Mockito.verify(_accountDao, Mockito.atLeastOnce()).markForCleanup(
                Mockito.eq(42l));
    }

    @Test
    public void testAuthenticateUser() throws UnknownHostException {
        final Pair<Boolean, UserAuthenticator.ActionOnFailedAuthentication> successAuthenticationPair = new Pair<>(true, null);
        final Pair<Boolean, UserAuthenticator.ActionOnFailedAuthentication> failureAuthenticationPair = new Pair<>(false,
                UserAuthenticator.ActionOnFailedAuthentication.INCREMENT_INCORRECT_LOGIN_ATTEMPT_COUNT);

        final UserAccountVO userAccountVO = new UserAccountVO();
        userAccountVO.setSource(User.Source.UNKNOWN);
        userAccountVO.setState(Account.State.disabled.toString());
        Mockito.when(_userAccountDao.getUserAccount("test", 1L)).thenReturn(userAccountVO);
        Mockito.when(userAuthenticator.authenticate("test", "fail", 1L, null)).thenReturn(failureAuthenticationPair);
        Mockito.when(userAuthenticator.authenticate("test", null, 1L, null)).thenReturn(successAuthenticationPair);
        Mockito.when(userAuthenticator.authenticate("test", "", 1L, null)).thenReturn(successAuthenticationPair);

        //Test for incorrect password. authentication should fail
        UserAccount userAccount = accountManager.authenticateUser("test", "fail", 1L, InetAddress.getByName("127.0.0.1"), null);
        Assert.assertNull(userAccount);

        //Test for null password. authentication should fail
        userAccount = accountManager.authenticateUser("test", null, 1L, InetAddress.getByName("127.0.0.1"), null);
        Assert.assertNull(userAccount);

        //Test for empty password. authentication should fail
        userAccount = accountManager.authenticateUser("test", "", 1L, InetAddress.getByName("127.0.0.1"), null);
        Assert.assertNull(userAccount);

        //Verifying that the authentication method is only called when password is specified
        Mockito.verify(userAuthenticator, Mockito.times(1)).authenticate("test", "fail", 1L, null);
        Mockito.verify(userAuthenticator, Mockito.never()).authenticate("test", null, 1L, null);
        Mockito.verify(userAuthenticator, Mockito.never()).authenticate("test", "", 1L, null);
    }
}
