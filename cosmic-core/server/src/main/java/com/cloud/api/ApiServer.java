package com.cloud.api;

import com.cloud.acl.APIChecker;
import com.cloud.api.ResponseObject.ResponseView;
import com.cloud.api.auth.APIAuthenticationManager;
import com.cloud.api.command.admin.account.ListAccountsCmdByAdmin;
import com.cloud.api.command.admin.host.ListHostsCmd;
import com.cloud.api.command.admin.router.ListRoutersCmd;
import com.cloud.api.command.admin.storage.ListStoragePoolsCmd;
import com.cloud.api.command.admin.user.ListUsersCmd;
import com.cloud.api.command.admin.vm.ListVMsCmdByAdmin;
import com.cloud.api.command.admin.volume.ListVolumesCmdByAdmin;
import com.cloud.api.command.admin.zone.ListZonesCmdByAdmin;
import com.cloud.api.command.user.account.ListAccountsCmd;
import com.cloud.api.command.user.account.ListProjectAccountsCmd;
import com.cloud.api.command.user.event.ListEventsCmd;
import com.cloud.api.command.user.offering.ListDiskOfferingsCmd;
import com.cloud.api.command.user.offering.ListServiceOfferingsCmd;
import com.cloud.api.command.user.project.ListProjectInvitationsCmd;
import com.cloud.api.command.user.project.ListProjectsCmd;
import com.cloud.api.command.user.tag.ListTagsCmd;
import com.cloud.api.command.user.vm.ListVMsCmd;
import com.cloud.api.command.user.vmgroup.ListVMGroupsCmd;
import com.cloud.api.command.user.volume.ListVolumesCmd;
import com.cloud.api.command.user.zone.ListZonesCmd;
import com.cloud.api.dispatch.DispatchChainFactory;
import com.cloud.api.dispatch.DispatchTask;
import com.cloud.api.response.ApiResponseSerializer;
import com.cloud.api.response.AsyncJobResponse;
import com.cloud.api.response.CreateCmdResponse;
import com.cloud.api.response.ExceptionResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.LoginCmdResponse;
import com.cloud.config.ApiServiceConfiguration;
import com.cloud.configuration.Config;
import com.cloud.context.CallContext;
import com.cloud.dao.EntityManager;
import com.cloud.dao.UUIDManager;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.event.ActionEventUtils;
import com.cloud.event.EventCategory;
import com.cloud.event.EventTypes;
import com.cloud.framework.config.dao.ConfigurationDao;
import com.cloud.framework.config.impl.ConfigurationVO;
import com.cloud.framework.events.Event;
import com.cloud.framework.events.EventBus;
import com.cloud.framework.events.EventBusException;
import com.cloud.framework.jobs.AsyncJob;
import com.cloud.framework.jobs.AsyncJobManager;
import com.cloud.framework.jobs.impl.AsyncJobVO;
import com.cloud.framework.messagebus.MessageBus;
import com.cloud.framework.messagebus.MessageDispatcher;
import com.cloud.framework.messagebus.MessageHandler;
import com.cloud.legacymodel.domain.Domain;
import com.cloud.legacymodel.exceptions.AccountLimitException;
import com.cloud.legacymodel.exceptions.CloudAuthenticationException;
import com.cloud.legacymodel.exceptions.CloudRuntimeException;
import com.cloud.legacymodel.exceptions.ExceptionProxyObject;
import com.cloud.legacymodel.exceptions.InsufficientCapacityException;
import com.cloud.legacymodel.exceptions.InvalidParameterValueException;
import com.cloud.legacymodel.exceptions.PermissionDeniedException;
import com.cloud.legacymodel.exceptions.RequestLimitException;
import com.cloud.legacymodel.exceptions.ResourceAllocationException;
import com.cloud.legacymodel.exceptions.ResourceUnavailableException;
import com.cloud.legacymodel.user.Account;
import com.cloud.legacymodel.user.User;
import com.cloud.legacymodel.user.UserAccount;
import com.cloud.legacymodel.utils.Pair;
import com.cloud.common.managed.context.ManagedContextRunnable;
import com.cloud.user.AccountManager;
import com.cloud.user.DomainManager;
import com.cloud.user.UserVO;
import com.cloud.utils.ConstantTimeComparator;
import com.cloud.utils.HttpUtils;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.StringUtils;
import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.TransactionLegacy;
import com.cloud.utils.net.NetUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import javax.naming.ConfigurationException;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.reflect.TypeToken;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpServerConnection;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.SocketHttpServerConnection;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.stereotype.Component;

@Component
public class ApiServer extends ManagerBase implements HttpRequestHandler, ApiServerService {
    /**
     * Non-printable ASCII characters - numbers 0 to 31 and 127 decimal
     */
    public static final String CONTROL_CHARACTERS = "[\000-\011\013-\014\016-\037\177]";
    private static final Logger s_logger = LoggerFactory.getLogger(ApiServer.class.getName());
    private static final DateFormat DateFormatToUse = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final Map<String, List<Class<?>>> s_apiNameCmdClassMap = new HashMap<>();
    private static final ExecutorService s_executor = new ThreadPoolExecutor(10, 150, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new NamedThreadFactory(
            "ApiServer"));
    public static boolean encodeApiResponse = false;
    public static boolean s_enableSecureCookie = false;
    public static String s_jsonContentType = HttpUtils.JSON_CONTENT_TYPE;
    private static int s_workerCount = 0;
    @Inject
    protected ApiDispatcher _dispatcher;
    @Inject
    protected DispatchChainFactory dispatchChainFactory;
    @Inject
    protected ApiAsyncJobDispatcher _asyncDispatcher;
    @Inject
    APIAuthenticationManager _authManager;
    List<PluggableService> _pluggableServices;
    List<APIChecker> _apiAccessCheckers;
    @Inject
    MessageBus _messageBus;
    @Inject
    private AccountManager _accountMgr;
    @Inject
    private DomainManager _domainMgr;
    @Inject
    private DomainDao _domainDao;
    @Inject
    private UUIDManager _uuidMgr;
    @Inject
    private AsyncJobManager _asyncMgr;
    @Inject
    private ConfigurationDao _configDao;
    @Inject
    private EntityManager _entityMgr;

    public ApiServer() {
    }

    public static boolean isEncodeApiResponse() {
        return encodeApiResponse;
    }

    private static void setEncodeApiResponse(final boolean encodeApiResponse) {
        ApiServer.encodeApiResponse = encodeApiResponse;
    }

    public static boolean isSecureSessionCookieEnabled() {
        return s_enableSecureCookie;
    }

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        _messageBus.subscribe(AsyncJob.Topics.JOB_EVENT_PUBLISH, MessageDispatcher.getDispatcher(this));
        return true;
    }

    @Override
    public boolean start() {
        Integer apiPort = null; // api port, null by default
        final SearchCriteria<ConfigurationVO> sc = _configDao.createSearchCriteria();
        sc.addAnd("name", SearchCriteria.Op.EQ, Config.IntegrationAPIPort.key());
        final List<ConfigurationVO> values = _configDao.search(sc, null);
        if ((values != null) && (values.size() > 0)) {
            final ConfigurationVO apiPortConfig = values.get(0);
            if (apiPortConfig.getValue() != null) {
                apiPort = Integer.parseInt(apiPortConfig.getValue());
            }
        }

        final Map<String, String> configs = _configDao.getConfiguration();
        final String strSnapshotLimit = configs.get(Config.ConcurrentSnapshotsThresholdPerHost.key());
        if (strSnapshotLimit != null) {
            final Long snapshotLimit = NumbersUtil.parseLong(strSnapshotLimit, 1L);
            if (snapshotLimit.longValue() <= 0) {
                s_logger.debug("Global config parameter " + Config.ConcurrentSnapshotsThresholdPerHost.toString() + " is less or equal 0; defaulting to unlimited");
            } else {
                _dispatcher.setCreateSnapshotQueueSizeLimit(snapshotLimit);
            }
        }

        final Set<Class<?>> cmdClasses = new HashSet<>();
        for (final PluggableService pluggableService : _pluggableServices) {
            cmdClasses.addAll(pluggableService.getCommands());
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Discovered plugin " + pluggableService.getClass().getSimpleName());
            }
        }

        for (final Class<?> cmdClass : cmdClasses) {
            final APICommand at = cmdClass.getAnnotation(APICommand.class);
            if (at == null) {
                throw new CloudRuntimeException(String.format("%s is claimed as a API command, but it doesn't have @APICommand annotation", cmdClass.getName()));
            }

            final String apiName = at.name();
            List<Class<?>> apiCmdList = s_apiNameCmdClassMap.get(apiName);
            if (apiCmdList == null) {
                apiCmdList = new ArrayList<>();
                s_apiNameCmdClassMap.put(apiName, apiCmdList);
            }
            apiCmdList.add(cmdClass);
        }

        setEncodeApiResponse(Boolean.valueOf(_configDao.getValue(Config.EncodeApiResponse.key())));
        final String jsonType = _configDao.getValue(Config.JSONDefaultContentType.key());
        if (jsonType != null) {
            s_jsonContentType = jsonType;
        }
        final Boolean enableSecureSessionCookie = Boolean.valueOf(_configDao.getValue(Config.EnableSecureSessionCookie.key()));
        if (enableSecureSessionCookie != null) {
            s_enableSecureCookie = enableSecureSessionCookie;
        }

        if (apiPort != null) {
            final ListenerThread listenerThread = new ListenerThread(this, apiPort);
            listenerThread.start();
        }

        return true;
    }

    @MessageHandler(topic = AsyncJob.Topics.JOB_EVENT_PUBLISH)
    private void handleAsyncJobPublishEvent(final String subject, final String senderAddress, final Object args) {
        assert (args != null);

        final Pair<AsyncJob, String> eventInfo = (Pair<AsyncJob, String>) args;
        final AsyncJob job = eventInfo.first();
        final String jobEvent = eventInfo.second();

        if (s_logger.isTraceEnabled()) {
            s_logger.trace("Handle asyjob publish event " + jobEvent);
        }

        final EventBus eventBus;
        try {
            eventBus = ComponentContext.getComponent(EventBus.class);
        } catch (final NoSuchBeanDefinitionException nbe) {
            return; // no provider is configured to provide events bus, so just return
        }

        if (!job.getDispatcher().equalsIgnoreCase("ApiAsyncJobDispatcher")) {
            return;
        }

        final User userJobOwner = _accountMgr.getUserIncludingRemoved(job.getUserId());
        final Account jobOwner = _accountMgr.getAccount(userJobOwner.getAccountId());

        // Get the event type from the cmdInfo json string
        final String info = job.getCmdInfo();
        String cmdEventType = "unknown";
        if (info != null) {
            final Type type = new TypeToken<Map<String, String>>() {
            }.getType();
            final Map<String, String> cmdInfo = ApiGsonHelper.getBuilder().create().fromJson(info, type);
            final String eventTypeObj = cmdInfo.get("cmdEventType");
            if (eventTypeObj != null) {
                cmdEventType = eventTypeObj;
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Retrieved cmdEventType from job info: " + cmdEventType);
                }
            } else {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Unable to locate cmdEventType marker in job info. publish as unknown event");
                }
            }
        }
        // For some reason, the instanceType / instanceId are not abstract, which means we may get null values.
        final String instanceType = job.getInstanceType() != null ? job.getInstanceType() : "unknown";
        final String instanceUuid = job.getInstanceId() != null ? ApiDBUtils.findJobInstanceUuid(job) : "";
        final Event event = new Event("management-server", EventCategory.ASYNC_JOB_CHANGE_EVENT
                .getName(),
                jobEvent, instanceType, instanceUuid);

        final Map<String, String> eventDescription = new HashMap<>();
        eventDescription.put("command", job.getCmd());
        eventDescription.put("user", userJobOwner.getUuid());
        eventDescription.put("account", jobOwner.getUuid());
        eventDescription.put("processStatus", "" + job.getProcessStatus());
        eventDescription.put("resultCode", "" + job.getResultCode());
        eventDescription.put("instanceUuid", instanceUuid);
        eventDescription.put("instanceType", instanceType);
        eventDescription.put("commandEventType", cmdEventType);
        eventDescription.put("jobId", job.getUuid());
        eventDescription.put("jobResult", job.getResult());
        eventDescription.put("cmdInfo", job.getCmdInfo());
        eventDescription.put("status", "" + job.getStatus());
        // If the event.accountinfo boolean value is set, get the human readable value for the username / domainname
        final Map<String, String> configs = _configDao.getConfiguration("management-server", new HashMap<String, String>());
        if (Boolean.valueOf(configs.get("event.accountinfo"))) {
            final DomainVO domain = _domainDao.findById(jobOwner.getDomainId());
            eventDescription.put("username", userJobOwner.getUsername());
            eventDescription.put("accountname", jobOwner.getAccountName());
            eventDescription.put("domainname", domain.getName());
        }
        event.setDescription(eventDescription);

        try {
            eventBus.publish(event);
        } catch (final EventBusException evx) {
            final String errMsg = "Failed to publish async job event on the the event bus.";
            s_logger.warn(errMsg, evx);
        }
    }

    // NOTE: handle() only handles over the wire (OTW) requests from integration.api.port 8096
    // If integration api port is not configured, actual OTW requests will be received by ApiServlet
    @Override
    public void handle(final HttpRequest request, final HttpResponse response, final HttpContext context) throws HttpException, IOException {

        // Create StringBuffer to log information in access log
        final StringBuilder sb = new StringBuilder();
        final HttpServerConnection connObj = (HttpServerConnection) context.getAttribute("http.connection");
        if (connObj instanceof SocketHttpServerConnection) {
            final InetAddress remoteAddr = ((SocketHttpServerConnection) connObj).getRemoteAddress();
            sb.append(remoteAddr.toString() + " -- ");
        }
        sb.append(StringUtils.cleanString(request.getRequestLine().toString()));

        try {
            List<NameValuePair> paramList = null;
            try {
                paramList = URLEncodedUtils.parse(new URI(request.getRequestLine().getUri()), HttpUtils.UTF_8);
            } catch (final URISyntaxException e) {
                s_logger.error("Error parsing url request", e);
            }

            // Use Multimap as the parameter map should be in the form (name=String, value=String[])
            // So parameter values are stored in a list for the same name key
            // APITODO: Use Guava's (import com.google.common.collect.Multimap;)
            // (Immutable)Multimap<String, String> paramMultiMap = HashMultimap.create();
            // Map<String, Collection<String>> parameterMap = paramMultiMap.asMap();
            final Map parameterMap = new HashMap<>();
            String responseType = HttpUtils.RESPONSE_TYPE_XML;
            if (paramList != null) {
                for (final NameValuePair param : paramList) {
                    if (param.getName().equalsIgnoreCase("response")) {
                        responseType = param.getValue();
                        continue;
                    }
                    parameterMap.put(param.getName(), new String[]{param.getValue()});
                }
            }

            // Get the type of http method being used.
            parameterMap.put("httpmethod", new String[]{request.getRequestLine().getMethod()});

            // Check responseType, if not among valid types, fallback to JSON
            if (!(responseType.equals(HttpUtils.RESPONSE_TYPE_JSON) || responseType.equals(HttpUtils.RESPONSE_TYPE_XML))) {
                responseType = HttpUtils.RESPONSE_TYPE_XML;
            }

            try {
                // always trust commands from API port, user context will always be UID_SYSTEM/ACCOUNT_ID_SYSTEM
                CallContext.register(_accountMgr.getSystemUser(), _accountMgr.getSystemAccount());
                sb.insert(0, "(userId=" + User.UID_SYSTEM + " accountId=" + Account.ACCOUNT_ID_SYSTEM + " sessionId=" + null + ") ");
                final String responseText = handleRequest(parameterMap, responseType, sb);
                sb.append(" 200 " + ((responseText == null) ? 0 : responseText.length()));

                writeResponse(response, responseText, HttpStatus.SC_OK, responseType, null);
            } catch (final ServerApiException se) {
                final String responseText = getSerializedApiError(se, parameterMap, responseType);
                writeResponse(response, responseText, se.getErrorCode().getHttpCode(), responseType, se.getDescription());
                sb.append(" " + se.getErrorCode() + " " + se.getDescription());
            } catch (final RuntimeException e) {
                // log runtime exception like NullPointerException to help identify the source easier
                s_logger.error("Unhandled exception, ", e);
                throw e;
            }
        } finally {
            s_logger.info(sb.toString());
            CallContext.unregister();
        }
    }

    // FIXME: rather than isError, we might was to pass in the status code to give more flexibility
    private void writeResponse(final HttpResponse resp, final String responseText, final int statusCode, final String responseType, final String reasonPhrase) {
        try {
            resp.setStatusCode(statusCode);
            resp.setReasonPhrase(reasonPhrase);

            final BasicHttpEntity body = new BasicHttpEntity();
            if (HttpUtils.RESPONSE_TYPE_JSON.equalsIgnoreCase(responseType)) {
                // JSON response
                body.setContentType(getJSONContentType());
                if (responseText == null) {
                    body.setContent(new ByteArrayInputStream("{ \"error\" : { \"description\" : \"Internal Server Error\" } }".getBytes(HttpUtils.UTF_8)));
                }
            } else {
                body.setContentType("text/xml");
                if (responseText == null) {
                    body.setContent(new ByteArrayInputStream("<error>Internal Server Error</error>".getBytes(HttpUtils.UTF_8)));
                }
            }

            if (responseText != null) {
                body.setContent(new ByteArrayInputStream(responseText.getBytes(HttpUtils.UTF_8)));
            }
            resp.setEntity(body);
        } catch (final Exception ex) {
            s_logger.error("error!", ex);
        }
    }

    public void checkCharacterInkParams(final Map params) {
        final Map<String, String> stringMap = new HashMap<>();
        final Set keys = params.keySet();
        final Iterator keysIter = keys.iterator();
        while (keysIter.hasNext()) {
            final String key = (String) keysIter.next();
            final String[] value = (String[]) params.get(key);
            // fail if parameter value contains ASCII control (non-printable) characters
            if (value[0] != null) {
                final Pattern pattern = Pattern.compile(CONTROL_CHARACTERS);
                final Matcher matcher = pattern.matcher(value[0]);
                if (matcher.find()) {
                    throw new ServerApiException(ApiErrorCode.PARAM_ERROR, "Received value " + value[0] + " for parameter " + key +
                            " is invalid, contains illegal ASCII non-printable characters");
                }
            }
            stringMap.put(key, value[0]);
        }
    }

    private String queueCommand(final BaseCmd cmdObj, final Map<String, String> params, final StringBuilder log) throws Exception {
        final CallContext ctx = CallContext.current();
        final Long callerUserId = ctx.getCallingUserId();
        final Account caller = ctx.getCallingAccount();

        // Queue command based on Cmd super class:
        // BaseCmd: cmd is dispatched to ApiDispatcher, executed, serialized and returned.
        // BaseAsyncCreateCmd: cmd params are processed and create() is called, then same workflow as BaseAsyncCmd.
        // BaseAsyncCmd: cmd is processed and submitted as an AsyncJob, job related info is serialized and returned.
        if (cmdObj instanceof BaseAsyncCmd) {
            Long objectId = null;
            final String objectUuid;
            if (cmdObj instanceof BaseAsyncCreateCmd) {
                final BaseAsyncCreateCmd createCmd = (BaseAsyncCreateCmd) cmdObj;
                _dispatcher.dispatchCreateCmd(createCmd, params);
                objectId = createCmd.getEntityId();
                objectUuid = createCmd.getEntityUuid();
                params.put("id", objectId.toString());
                final Class entityClass = EventTypes.getEntityClassForEvent(createCmd.getEventType());
                if (entityClass != null) {
                    ctx.putContextParameter(entityClass, objectUuid);
                }
            } else {
                // Extract the uuid before params are processed and id reflects internal db id
                objectUuid = params.get(ApiConstants.ID);
                dispatchChainFactory.getStandardDispatchChain().dispatch(new DispatchTask(cmdObj, params));
            }

            final BaseAsyncCmd asyncCmd = (BaseAsyncCmd) cmdObj;

            if (callerUserId != null) {
                params.put("ctxUserId", callerUserId.toString());
            }
            if (caller != null) {
                params.put("ctxAccountId", String.valueOf(caller.getId()));
            }
            if (objectUuid != null) {
                params.put("uuid", objectUuid);
            }

            long startEventId = ctx.getStartEventId();
            asyncCmd.setStartEventId(startEventId);

            // save the scheduled event
            final Long eventId =
                    ActionEventUtils.onScheduledActionEvent((callerUserId == null) ? (Long) User.UID_SYSTEM : callerUserId, asyncCmd.getEntityOwnerId(), asyncCmd.getEventType(),
                            asyncCmd.getEventDescription(), asyncCmd.isDisplay(), startEventId);
            if (startEventId == 0) {
                // There was no create event before, set current event id as start eventId
                startEventId = eventId;
            }

            params.put("ctxStartEventId", String.valueOf(startEventId));
            params.put("cmdEventType", asyncCmd.getEventType().toString());
            params.put("ctxDetails", ApiGsonHelper.getBuilder().create().toJson(ctx.getContextParameters()));

            final Long instanceId = (objectId == null) ? asyncCmd.getInstanceId() : objectId;

            // users can provide the job id they want to use, so log as it is a uuid and is unique
            final String injectedJobId = asyncCmd.getInjectedJobId();
            _uuidMgr.checkUuidSimple(injectedJobId, AsyncJob.class);

            final AsyncJobVO job = new AsyncJobVO("", callerUserId, caller.getId(), cmdObj.getClass().getName(),
                    ApiGsonHelper.getBuilder().create().toJson(params), instanceId,
                    asyncCmd.getInstanceType() != null ? asyncCmd.getInstanceType().toString() : null,
                    injectedJobId);
            job.setDispatcher(_asyncDispatcher.getName());

            final long jobId = _asyncMgr.submitAsyncJob(job);

            if (jobId == 0L) {
                final String errorMsg = "Unable to schedule async job for command " + job.getCmd();
                s_logger.warn(errorMsg);
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, errorMsg);
            }
            final String response;
            if (objectId != null) {
                final String objUuid = (objectUuid == null) ? objectId.toString() : objectUuid;
                response = getBaseAsyncCreateResponse(jobId, (BaseAsyncCreateCmd) asyncCmd, objUuid);
            } else {
                SerializationContext.current().setUuidTranslation(true);
                response = getBaseAsyncResponse(jobId, asyncCmd);
            }
            // Always log response for async for now, I don't think any sensitive data will be in here.
            // It might be nice to send this through scrubbing similar to how
            // ApiResponseSerializer.toSerializedStringWithSecureLogs works. For now, this gets jobid's
            // in the api logs.
            log.append(response);
            return response;
        } else {
            _dispatcher.dispatch(cmdObj, params, false);

            // if the command is of the listXXXCommand, we will need to also return the
            // the job id and status if possible
            // For those listXXXCommand which we have already created DB views, this step is not needed since async job is joined in their db views.
            if (cmdObj instanceof BaseListCmd && !(cmdObj instanceof ListVMsCmd) && !(cmdObj instanceof ListVMsCmdByAdmin) && !(cmdObj instanceof ListRoutersCmd) &&
                    !(cmdObj instanceof ListTagsCmd) && !(cmdObj instanceof ListEventsCmd) && !(cmdObj instanceof ListVMGroupsCmd) && !(cmdObj instanceof ListProjectsCmd) &&
                    !(cmdObj instanceof ListProjectAccountsCmd) && !(cmdObj instanceof ListProjectInvitationsCmd) && !(cmdObj instanceof ListHostsCmd) &&
                    !(cmdObj instanceof ListVolumesCmd) && !(cmdObj instanceof ListVolumesCmdByAdmin) && !(cmdObj instanceof ListUsersCmd) && !(cmdObj instanceof ListAccountsCmd)
                    && !(cmdObj instanceof ListAccountsCmdByAdmin) &&
                    !(cmdObj instanceof ListStoragePoolsCmd) && !(cmdObj instanceof ListDiskOfferingsCmd) && !(cmdObj instanceof ListServiceOfferingsCmd) &&
                    !(cmdObj instanceof ListZonesCmd) && !(cmdObj instanceof ListZonesCmdByAdmin)) {
                buildAsyncListResponse((BaseListCmd) cmdObj, caller);
            }

            SerializationContext.current().setUuidTranslation(true);
            return ApiResponseSerializer.toSerializedStringWithSecureLogs((ResponseObject) cmdObj.getResponseObject(), cmdObj.getResponseType(), log);
        }
    }

    private void buildAuditTrail(final StringBuilder auditTrailSb, final String command, final String result) {
        if (result == null) {
            return;
        }
        auditTrailSb.append(" " + HttpServletResponse.SC_OK + " ");
        if (command.equals("createSSHKeyPair")) {
            auditTrailSb.append("This result was not logged because it contains sensitive data.");
        } else {
            auditTrailSb.append(result);
        }
    }

    private String printCommand(final String[] command) {
        return StringUtils.join(Arrays.asList(command), "; ");
    }

    public static String getJSONContentType() {
        return s_jsonContentType;
    }

    private String getBaseAsyncCreateResponse(final long jobId, final BaseAsyncCreateCmd cmd, final String objectUuid) {
        final CreateCmdResponse response = new CreateCmdResponse();
        final AsyncJob job = _entityMgr.findById(AsyncJob.class, jobId);
        response.setJobId(job.getUuid());
        response.setId(objectUuid);
        response.setResponseName(cmd.getCommandName());
        return ApiResponseSerializer.toSerializedString(response, cmd.getResponseType());
    }

    private String getBaseAsyncResponse(final long jobId, final BaseAsyncCmd cmd) {
        final AsyncJobResponse response = new AsyncJobResponse();

        final AsyncJob job = _entityMgr.findById(AsyncJob.class, jobId);
        response.setJobId(job.getUuid());
        response.setResponseName(cmd.getCommandName());
        return ApiResponseSerializer.toSerializedString(response, cmd.getResponseType());
    }

    private void buildAsyncListResponse(final BaseListCmd command, final Account account) {
        final List<ResponseObject> responses = ((ListResponse) command.getResponseObject()).getResponses();
        if (responses != null && responses.size() > 0) {
            final List<? extends AsyncJob> jobs;

            // list all jobs for ROOT admin
            if (_accountMgr.isRootAdmin(account.getId())) {
                jobs = _asyncMgr.findInstancePendingAsyncJobs(command.getInstanceType().toString(), null);
            } else {
                jobs = _asyncMgr.findInstancePendingAsyncJobs(command.getInstanceType().toString(), account.getId());
            }

            if (jobs.size() == 0) {
                return;
            }

            final Map<String, AsyncJob> objectJobMap = new HashMap<>();
            for (final AsyncJob job : jobs) {
                if (job.getInstanceId() == null) {
                    continue;
                }
                final String instanceUuid = ApiDBUtils.findJobInstanceUuid(job);
                objectJobMap.put(instanceUuid, job);
            }

            for (final ResponseObject response : responses) {
                if (response.getObjectId() != null && objectJobMap.containsKey(response.getObjectId())) {
                    final AsyncJob job = objectJobMap.get(response.getObjectId());
                    response.setJobId(job.getUuid());
                    response.setJobStatus(job.getStatus().ordinal());
                }
            }
        }
    }

    @Override
    public boolean verifyRequest(final Map<String, Object[]> requestParameters, final Long userId, String remoteAddress) throws ServerApiException {
        try {
            String apiKey = null;
            final String secretKey;
            String signature = null;
            String unsignedRequest = null;

            final String[] command = (String[]) requestParameters.get(ApiConstants.COMMAND);
            if (command == null) {
                s_logger.info("missing command, ignoring request...");
                return false;
            }

            final String commandName = command[0];

            // if userId not null, that mean that user is logged in
            if (userId != null) {
                final User user = ApiDBUtils.findUserById(userId);

                if (!commandAvailable(remoteAddress, commandName, user)) {
                    return false;
                }
                return true;
            } else {
                // check against every available command to see if the command exists or not
                if (!s_apiNameCmdClassMap.containsKey(commandName) && !commandName.equals("login") && !commandName.equals("logout")) {
                    final String errorMessage = "The given command '" + commandName + "' either does not exist, is not available" +
                            " for user, or not available from ip address '" + remoteAddress + "'.";
                    s_logger.debug(errorMessage);
                    return false;
                }
            }

            // - build a request string with sorted params, make sure it's all lowercase
            // - sign the request, verify the signature is the same
            final List<String> parameterNames = new ArrayList<>();

            for (final Object paramNameObj : requestParameters.keySet()) {
                parameterNames.add((String) paramNameObj); // put the name in a list that we'll sort later
            }

            Collections.sort(parameterNames);

            String signatureVersion = null;
            String expires = null;

            for (final String paramName : parameterNames) {
                // parameters come as name/value pairs in the form String/String[]
                final String paramValue = ((String[]) requestParameters.get(paramName))[0];

                if (ApiConstants.SIGNATURE.equalsIgnoreCase(paramName)) {
                    signature = paramValue;
                } else {
                    if (ApiConstants.API_KEY.equalsIgnoreCase(paramName)) {
                        apiKey = paramValue;
                    } else if (ApiConstants.SIGNATURE_VERSION.equalsIgnoreCase(paramName)) {
                        signatureVersion = paramValue;
                    } else if (ApiConstants.EXPIRES.equalsIgnoreCase(paramName)) {
                        expires = paramValue;
                    }

                    if (unsignedRequest == null) {
                        unsignedRequest = paramName + "=" + URLEncoder.encode(paramValue, HttpUtils.UTF_8).replaceAll("\\+", "%20");
                    } else {
                        unsignedRequest = unsignedRequest + "&" + paramName + "=" + URLEncoder.encode(paramValue, HttpUtils.UTF_8).replaceAll("\\+", "%20");
                    }
                }
            }

            // if api/secret key are passed to the parameters
            if ((signature == null) || (apiKey == null)) {
                s_logger.debug("Expired session, missing signature, or missing apiKey -- ignoring request. Signature: " + signature + ", apiKey: " + apiKey);
                return false; // no signature, bad request
            }

            final Date expiresTS;
            // FIXME: Hard coded signature, why not have an enum
            if ("3".equals(signatureVersion)) {
                // New signature authentication. Check for expire parameter and its validity
                if (expires == null) {
                    s_logger.debug("Missing Expires parameter -- ignoring request. Signature: " + signature + ", apiKey: " + apiKey);
                    return false;
                }
                synchronized (DateFormatToUse) {
                    try {
                        expiresTS = DateFormatToUse.parse(expires);
                    } catch (final ParseException pe) {
                        s_logger.debug("Incorrect date format for Expires parameter", pe);
                        return false;
                    }
                }
                final Date now = new Date(System.currentTimeMillis());
                if (expiresTS.before(now)) {
                    s_logger.debug("Request expired -- ignoring ...sig: " + signature + ", apiKey: " + apiKey);
                    return false;
                }
            }

            final TransactionLegacy txn = TransactionLegacy.open(TransactionLegacy.CLOUD_DB);
            txn.close();
            final User user;
            // verify there is a user with this api key
            final Pair<User, Account> userAcctPair = _accountMgr.findUserByApiKey(apiKey);
            if (userAcctPair == null) {
                s_logger.debug("apiKey does not map to a valid user -- ignoring request, apiKey: " + apiKey);
                return false;
            }

            user = userAcctPair.first();
            final Account account = userAcctPair.second();

            if (user.getState() != Account.State.enabled || !account.getState().equals(Account.State.enabled)) {
                s_logger.info("disabled or locked user accessing the api, userid = " + user.getId() + "; name = " + user.getUsername() + "; state: " + user.getState() +
                        "; accountState: " + account.getState());
                return false;
            }

            if (!commandAvailable(remoteAddress, commandName, user)) {
                return false;
            }

            // verify secret key exists
            secretKey = user.getSecretKey();
            if (secretKey == null) {
                s_logger.info("User does not have a secret key associated with the account -- ignoring request, username: " + user.getUsername());
                return false;
            }

            unsignedRequest = unsignedRequest.toLowerCase();

            final Mac mac = Mac.getInstance("HmacSHA1");
            final SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA1");
            mac.init(keySpec);
            mac.update(unsignedRequest.getBytes());

            final byte[] encryptedBytes = mac.doFinal();
            final String computedSignature = Base64.encodeBase64String(encryptedBytes);
            final boolean equalSig = ConstantTimeComparator.compareStrings(signature, computedSignature);

            if (!equalSig) {
                s_logger.info("User signature: " + signature + " is not equaled to computed signature: " + computedSignature);
            } else {
                CallContext.register(user, account);
            }
            return equalSig;
        } catch (final ServerApiException ex) {
            throw ex;
        } catch (final Exception ex) {
            s_logger.error("unable to verify request signature");
        }
        return false;
    }

    private boolean commandAvailable(final String remoteAddress, final String commandName, final User user) {
        try {
            checkCommandAvailable(user, commandName, remoteAddress);
        } catch (final RequestLimitException ex) {
            s_logger.debug(ex.getMessage());
            throw new ServerApiException(ApiErrorCode.API_LIMIT_EXCEED, ex.getMessage());
        } catch (final PermissionDeniedException ex) {
            final String errorMessage = "The given command '" + commandName + "' either does not exist, is not available" +
                    " for user, or not available from ip address '" + remoteAddress + "'.";
            s_logger.debug(errorMessage);
            return false;
        }
        return true;
    }

    @Override
    public Long fetchDomainId(final String domainUUID) {
        final Domain domain = _domainMgr.getDomain(domainUUID);
        if (domain != null) {
            return domain.getId();
        } else {
            return null;
        }
    }

    @Override
    public ResponseObject loginUser(final HttpSession session, final String username, final String password, Long domainId, final String domainPath, final InetAddress
            loginIpAddress,
                                    final Map<String, Object[]> requestParameters) throws CloudAuthenticationException {
        // We will always use domainId first. If that does not exist, we will use domain name. If THAT doesn't exist
        // we will default to ROOT
        if (domainId == null) {
            if (domainPath == null || domainPath.trim().length() == 0) {
                domainId = Domain.ROOT_DOMAIN;
            } else {
                final Domain domainObj = _domainMgr.findDomainByPath(domainPath);
                if (domainObj != null) {
                    domainId = domainObj.getId();
                } else { // if an unknown path is passed in, fail the login call
                    throw new CloudAuthenticationException("Unable to find the domain from the path " + domainPath);
                }
            }
        }

        final UserAccount userAcct = _accountMgr.authenticateUser(username, password, domainId, loginIpAddress, requestParameters);
        if (userAcct != null) {
            final String timezone = userAcct.getTimezone();
            float offsetInHrs = 0f;
            if (timezone != null) {
                final TimeZone t = TimeZone.getTimeZone(timezone);
                s_logger.info("Current user logged in under " + timezone + " timezone");

                final java.util.Date date = new java.util.Date();
                final long longDate = date.getTime();
                final float offsetInMs = (t.getOffset(longDate));
                offsetInHrs = offsetInMs / (1000 * 60 * 60);
                s_logger.info("Timezone offset from UTC is: " + offsetInHrs);
            }

            final Account account = _accountMgr.getAccount(userAcct.getAccountId());

            // set the userId and account object for everyone
            session.setAttribute("userid", userAcct.getId());
            final UserVO user = (UserVO) _accountMgr.getActiveUser(userAcct.getId());
            if (user.getUuid() != null) {
                session.setAttribute("user_UUID", user.getUuid());
            }

            session.setAttribute("username", userAcct.getUsername());
            session.setAttribute("firstname", userAcct.getFirstname());
            session.setAttribute("lastname", userAcct.getLastname());
            session.setAttribute("accountobj", account);
            session.setAttribute("account", account.getAccountName());

            session.setAttribute("domainid", account.getDomainId());
            final DomainVO domain = (DomainVO) _domainMgr.getDomain(account.getDomainId());
            if (domain.getUuid() != null) {
                session.setAttribute("domain_UUID", domain.getUuid());
            }
            if (domain.getName() != null) {
                session.setAttribute(ApiConstants.DOMAIN_NAME, domain.getName());
            }
            session.setAttribute("type", Short.valueOf(account.getType()).toString());
            session.setAttribute("registrationtoken", userAcct.getRegistrationToken());
            session.setAttribute("registered", Boolean.toString(userAcct.isRegistered()));

            if (timezone != null) {
                session.setAttribute("timezone", timezone);
                session.setAttribute("timezoneoffset", Float.valueOf(offsetInHrs).toString());
            }

            // (bug 5483) generate a session key that the user must submit on every request to prevent CSRF, add that
            // to the login response so that session-based authenticators know to send the key back
            final SecureRandom sesssionKeyRandom = new SecureRandom();
            final byte sessionKeyBytes[] = new byte[20];
            sesssionKeyRandom.nextBytes(sessionKeyBytes);
            final String sessionKey = Base64.encodeBase64URLSafeString(sessionKeyBytes);
            session.setAttribute(ApiConstants.SESSIONKEY, sessionKey);

            return createLoginResponse(session);
        }
        throw new CloudAuthenticationException("Failed to authenticate user " + username + " in domain " + domainId + "; please provide valid credentials");
    }

    private ResponseObject createLoginResponse(final HttpSession session) {
        final LoginCmdResponse response = new LoginCmdResponse();
        response.setTimeout(session.getMaxInactiveInterval());

        final String user_UUID = (String) session.getAttribute("user_UUID");
        response.setUserId(user_UUID);

        final String domain_UUID = (String) session.getAttribute("domain_UUID");
        response.setDomainId(domain_UUID);

        synchronized (session) {
            session.removeAttribute("user_UUID");
            session.removeAttribute("domain_UUID");
        }

        final Enumeration attrNames = session.getAttributeNames();
        if (attrNames != null) {
            while (attrNames.hasMoreElements()) {
                final String attrName = (String) attrNames.nextElement();
                final Object attrObj = session.getAttribute(attrName);
                if (ApiConstants.USERNAME.equalsIgnoreCase(attrName)) {
                    response.setUsername(attrObj.toString());
                }
                if (ApiConstants.ACCOUNT.equalsIgnoreCase(attrName)) {
                    response.setAccount(attrObj.toString());
                }
                if (ApiConstants.FIRSTNAME.equalsIgnoreCase(attrName)) {
                    response.setFirstName(attrObj.toString());
                }
                if (ApiConstants.LASTNAME.equalsIgnoreCase(attrName)) {
                    response.setLastName(attrObj.toString());
                }
                if (ApiConstants.TYPE.equalsIgnoreCase(attrName)) {
                    response.setType((attrObj.toString()));
                }
                if (ApiConstants.TIMEZONE.equalsIgnoreCase(attrName)) {
                    response.setTimeZone(attrObj.toString());
                }
                if (ApiConstants.REGISTERED.equalsIgnoreCase(attrName)) {
                    response.setRegistered(attrObj.toString());
                }
                if (ApiConstants.SESSIONKEY.equalsIgnoreCase(attrName)) {
                    response.setSessionKey(attrObj.toString());
                }
                if (ApiConstants.DOMAIN_NAME.equalsIgnoreCase(attrName)) {
                    response.setDomainName(attrObj.toString());
                }
            }
        }
        response.setResponseName("loginresponse");
        return response;
    }

    @Override
    public void logoutUser(final long userId) {
        _accountMgr.logoutUser(userId);
        return;
    }

    @Override
    public boolean verifyUser(final Long userId) {
        final User user = _accountMgr.getUserIncludingRemoved(userId);
        Account account = null;
        if (user != null) {
            account = _accountMgr.getAccount(user.getAccountId());
        }

        if ((user == null) || (user.getRemoved() != null) || !user.getState().equals(Account.State.enabled) || (account == null) ||
                !account.getState().equals(Account.State.enabled)) {
            s_logger.warn("Deleted/Disabled/Locked user with id=" + userId + " attempting to access public API");
            return false;
        }
        return true;
    }

    @Override
    public String getSerializedApiError(final int errorCode, final String errorText, final Map<String, Object[]> apiCommandParams, final String responseType) {
        String responseName = null;
        final Class<?> cmdClass;
        String responseText = null;

        try {
            if (apiCommandParams == null || apiCommandParams.isEmpty()) {
                responseName = "errorresponse";
            } else {
                final Object cmdObj = apiCommandParams.get(ApiConstants.COMMAND);
                // cmd name can be null when "command" parameter is missing in the request
                if (cmdObj != null) {
                    final String cmdName = ((String[]) cmdObj)[0];
                    cmdClass = getCmdClass(cmdName);
                    if (cmdClass != null) {
                        responseName = ((BaseCmd) cmdClass.newInstance()).getCommandName();
                    } else {
                        responseName = "errorresponse";
                    }
                }
            }
            final ExceptionResponse apiResponse = new ExceptionResponse();
            apiResponse.setErrorCode(errorCode);
            apiResponse.setErrorText(errorText);
            apiResponse.setResponseName(responseName);
            SerializationContext.current().setUuidTranslation(true);
            responseText = ApiResponseSerializer.toSerializedString(apiResponse, responseType);
        } catch (final Exception e) {
            s_logger.error("Exception responding to http request", e);
        }
        return responseText;
    }

    @Override
    public String getSerializedApiError(final ServerApiException ex, final Map<String, Object[]> apiCommandParams, final String responseType) {
        String responseName = null;
        final Class<?> cmdClass;
        String responseText = null;

        if (ex == null) {
            // this call should not be invoked with null exception
            return getSerializedApiError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Some internal error happened", apiCommandParams, responseType);
        }
        try {
            if (ex.getErrorCode() == ApiErrorCode.UNSUPPORTED_ACTION_ERROR || apiCommandParams == null || apiCommandParams.isEmpty()) {
                responseName = "errorresponse";
            } else {
                final Object cmdObj = apiCommandParams.get(ApiConstants.COMMAND);
                // cmd name can be null when "command" parameter is missing in
                // the request
                if (cmdObj != null) {
                    final String cmdName = ((String[]) cmdObj)[0];
                    cmdClass = getCmdClass(cmdName);
                    if (cmdClass != null) {
                        responseName = ((BaseCmd) cmdClass.newInstance()).getCommandName();
                    } else {
                        responseName = "errorresponse";
                    }
                }
            }
            final ExceptionResponse apiResponse = new ExceptionResponse();
            apiResponse.setErrorCode(ex.getErrorCode().getHttpCode());
            apiResponse.setErrorText(ExceptionUtils.getRootCauseMessage(ex));
            apiResponse.setResponseName(responseName);
            final ArrayList<ExceptionProxyObject> idList = ex.getIdProxyList();
            if (idList != null) {
                for (int i = 0; i < idList.size(); i++) {
                    apiResponse.addProxyObject(idList.get(i));
                }
            }
            // Also copy over the cserror code and the function/layer in which
            // it was thrown.
            apiResponse.setCSErrorCode(ex.getCSErrorCode());

            SerializationContext.current().setUuidTranslation(true);
            responseText = ApiResponseSerializer.toSerializedString(apiResponse, responseType);
        } catch (final Exception e) {
            s_logger.error("Exception responding to http request", e);
        }
        return responseText;
    }

    @Override
    public String handleRequest(final Map params, final String responseType, final StringBuilder auditTrailSb) throws ServerApiException {
        checkCharacterInkParams(params);

        final String response;
        String[] command = null;

        try {
            command = (String[]) params.get("command");
            if (command == null) {
                s_logger.error("invalid request, no command sent");
                if (s_logger.isTraceEnabled()) {
                    s_logger.trace("dumping request parameters");
                    for (final Object key : params.keySet()) {
                        final String keyStr = (String) key;
                        final String[] value = (String[]) params.get(key);
                        s_logger.trace("   key: " + keyStr + ", value: " + ((value == null) ? "'null'" : value[0]));
                    }
                }
                throw new ServerApiException(ApiErrorCode.UNSUPPORTED_ACTION_ERROR, "Invalid request, no command sent");
            } else {
                // Don't allow Login/Logout APIs to go past this point
                if (_authManager.getAPIAuthenticator(command[0]) != null) {
                    return null;
                }
                final Map<String, String> paramMap = new HashMap<>();
                final Set keys = params.keySet();
                final Iterator keysIter = keys.iterator();
                while (keysIter.hasNext()) {
                    final String key = (String) keysIter.next();
                    if ("command".equalsIgnoreCase(key)) {
                        continue;
                    }
                    final String[] value = (String[]) params.get(key);
                    paramMap.put(key, value[0]);
                }

                final Class<?> cmdClass = getCmdClass(command[0]);
                if (cmdClass != null) {
                    final APICommand annotation = cmdClass.getAnnotation(APICommand.class);
                    if (annotation == null) {
                        s_logger.error("No APICommand annotation found for class " + cmdClass.getCanonicalName());
                        throw new CloudRuntimeException("No APICommand annotation found for class " + cmdClass.getCanonicalName());
                    }

                    BaseCmd cmdObj = (BaseCmd) cmdClass.newInstance();
                    cmdObj = ComponentContext.inject(cmdObj);
                    cmdObj.configure();
                    cmdObj.setFullUrlParams(paramMap);
                    cmdObj.setResponseType(responseType);
                    cmdObj.setHttpMethod(paramMap.get(ApiConstants.HTTPMETHOD).toString());

                    // This is where the command is either serialized, or directly dispatched
                    final StringBuilder log = new StringBuilder();
                    response = queueCommand(cmdObj, paramMap, log);
                    buildAuditTrail(auditTrailSb, command[0], log.toString());
                } else {
                    final String errorString = "Unknown API command: " + command[0];
                    s_logger.warn(errorString);
                    auditTrailSb.append(" " + errorString);
                    throw new ServerApiException(ApiErrorCode.UNSUPPORTED_ACTION_ERROR, errorString);
                }
            }
        } catch (final InvalidParameterValueException ex) {
            s_logger.info(ex.getMessage());
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, ex.getMessage(), ex);
        } catch (final IllegalArgumentException ex) {
            s_logger.info(ex.getMessage());
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, ex.getMessage(), ex);
        } catch (final PermissionDeniedException ex) {
            final ArrayList<ExceptionProxyObject> idList = ex.getIdProxyList();
            if (idList != null) {
                final StringBuffer buf = new StringBuffer();
                for (final ExceptionProxyObject obj : idList) {
                    buf.append(obj.getDescription());
                    buf.append(":");
                    buf.append(obj.getUuid());
                    buf.append(" ");
                }
                s_logger.info("PermissionDenied: " + ex.getMessage() + " on objs: [" + buf.toString() + "]");
            } else {
                s_logger.info("PermissionDenied: " + ex.getMessage());
            }
            throw new ServerApiException(ApiErrorCode.ACCOUNT_ERROR, ex.getMessage(), ex);
        } catch (final AccountLimitException ex) {
            s_logger.info(ex.getMessage());
            throw new ServerApiException(ApiErrorCode.ACCOUNT_RESOURCE_LIMIT_ERROR, ex.getMessage(), ex);
        } catch (final InsufficientCapacityException ex) {
            s_logger.info(ex.getMessage());
            String errorMsg = ex.getMessage();
            if (!_accountMgr.isRootAdmin(CallContext.current().getCallingAccount().getId())) {
                // hide internal details to non-admin user for security reason
                errorMsg = BaseCmd.USER_ERROR_MESSAGE;
            }
            throw new ServerApiException(ApiErrorCode.INSUFFICIENT_CAPACITY_ERROR, errorMsg, ex);
        } catch (final ResourceAllocationException ex) {
            s_logger.info(ex.getMessage());
            throw new ServerApiException(ApiErrorCode.RESOURCE_ALLOCATION_ERROR, ex.getMessage(), ex);
        } catch (final ResourceUnavailableException ex) {
            s_logger.info(ex.getMessage());
            String errorMsg = ex.getMessage();
            if (!_accountMgr.isRootAdmin(CallContext.current().getCallingAccount().getId())) {
                // hide internal details to non-admin user for security reason
                errorMsg = BaseCmd.USER_ERROR_MESSAGE;
            }
            throw new ServerApiException(ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR, errorMsg, ex);
        } catch (final ServerApiException ex) {
            s_logger.info(ex.getDescription());
            throw ex;
        } catch (final Exception ex) {
            s_logger.error("Unhandled exception executing api command: " + ((command == null) ? "null" : printCommand(command)), ex);
            String errorMsg = ex.getMessage();
            if (!_accountMgr.isRootAdmin(CallContext.current().getCallingAccount().getId())) {
                // hide internal details to non-admin user for security reason
                errorMsg = BaseCmd.USER_ERROR_MESSAGE;
            }
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, errorMsg, ex);
        }

        return response;
    }

    @Override
    public Class<?> getCmdClass(final String cmdName) {
        final List<Class<?>> cmdList = s_apiNameCmdClassMap.get(cmdName);
        if (cmdList == null || cmdList.size() == 0) {
            return null;
        } else if (cmdList.size() == 1) {
            return cmdList.get(0);
        } else {
            // determine the cmd class based on calling context
            ResponseView view = ResponseView.Restricted;
            if (CallContext.current() != null
                    && _accountMgr.isRootAdmin(CallContext.current().getCallingAccount().getId())) {
                view = ResponseView.Full;
            }
            for (final Class<?> cmdClass : cmdList) {
                final APICommand at = cmdClass.getAnnotation(APICommand.class);
                if (at == null) {
                    throw new CloudRuntimeException(String.format("%s is claimed as a API command, but it doesn't have @APICommand annotation", cmdClass.getName()));
                }
                if (at.responseView() == null) {
                    throw new CloudRuntimeException(String.format(
                            "%s @APICommand annotation should specify responseView attribute to distinguish multiple command classes for a single api name", cmdClass.getName()));
                } else if (at.responseView() == view) {
                    return cmdClass;
                }
            }
            return null;
        }
    }

    private void checkCommandAvailable(final User user, final String commandName, final String remoteAddress) throws PermissionDeniedException {
        if (user == null) {
            throw new PermissionDeniedException("User is null for role based API access check for command" + commandName);
        }

        // Get the CIDRs from where this account is allowed to make calls
        final Account account = _accountMgr.getAccount(user.getAccountId());
        final String accessAllowedCidrs = ApiServiceConfiguration.ApiAllowedSourceCidrList.valueIn(account.getId()).replaceAll("\\s", "");
        final Boolean ApiSourceCidrChecksEnabled = ApiServiceConfiguration.ApiSourceCidrChecksEnabled.value();

        if (ApiSourceCidrChecksEnabled) {
            s_logger.debug("CIDRs from which account '" + account.toString() + "' is allowed to perform API calls: " + accessAllowedCidrs);
            InetAddress hostName = null;
            try {
                hostName = InetAddress.getByName(remoteAddress);
            } catch (final UnknownHostException e) {
                s_logger.warn("UnknownHostException when trying to lookup ip-address. Something is seriously wrong here. Blocking access.", e);
            }

            // Block when is not in the list of allowed IPs, or when hostname is unknown (didn't resolve to ip address)
            if (hostName == null || !NetUtils.isIpInCidrList(hostName, accessAllowedCidrs.split(","))) {
                s_logger.warn("Request by account '" + account.toString() + "' was denied since " + remoteAddress + " does not match " + accessAllowedCidrs);
                throw new PermissionDeniedException("Calls for domain '" + account.getAccountName() + "' are not allowed from ip address '"
                        + remoteAddress.replaceAll("/", "") + "'.");
            }
        }
        for (final APIChecker apiChecker : _apiAccessCheckers) {
            apiChecker.checkAccess(user, commandName);
        }
    }

    public List<PluggableService> getPluggableServices() {
        return _pluggableServices;
    }

    @Inject
    public void setPluggableServices(final List<PluggableService> pluggableServices) {
        _pluggableServices = pluggableServices;
    }

    public List<APIChecker> getApiAccessCheckers() {
        return _apiAccessCheckers;
    }

    @Inject
    public void setApiAccessCheckers(final List<APIChecker> apiAccessCheckers) {
        _apiAccessCheckers = apiAccessCheckers;
    }

    // FIXME: the following two threads are copied from
    // http://svn.apache.org/repos/asf/httpcomponents/httpcore/trunk/httpcore/src/examples/org/apache/http/examples/ElementalHttpServer.java
    // we have to cite a license if we are using this code directly, so we need to add the appropriate citation or
    // modify the
    // code to be very specific to our needs
    static class ListenerThread extends Thread {
        private HttpService _httpService = null;
        private ServerSocket _serverSocket = null;
        private HttpParams _params = null;

        public ListenerThread(final ApiServer requestHandler, final int port) {
            try {
                _serverSocket = new ServerSocket(port);
            } catch (final IOException ioex) {
                s_logger.error("error initializing api server", ioex);
                return;
            }

            _params = new BasicHttpParams();
            _params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 30000)
                   .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
                   .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
                   .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
                   .setParameter(CoreProtocolPNames.ORIGIN_SERVER, "HttpComponents/1.1");

            // Set up the HTTP protocol processor
            final BasicHttpProcessor httpproc = new BasicHttpProcessor();
            httpproc.addInterceptor(new ResponseDate());
            httpproc.addInterceptor(new ResponseServer());
            httpproc.addInterceptor(new ResponseContent());
            httpproc.addInterceptor(new ResponseConnControl());

            // Set up request handlers
            final HttpRequestHandlerRegistry reqistry = new HttpRequestHandlerRegistry();
            reqistry.register("*", requestHandler);

            // Set up the HTTP service
            _httpService = new HttpService(httpproc, new NoConnectionReuseStrategy(), new DefaultHttpResponseFactory());
            _httpService.setParams(_params);
            _httpService.setHandlerResolver(reqistry);
        }

        @Override
        public void run() {
            s_logger.info("ApiServer listening on port " + _serverSocket.getLocalPort());
            while (!Thread.interrupted()) {
                try {
                    // Set up HTTP connection
                    final Socket socket = _serverSocket.accept();
                    final DefaultHttpServerConnection conn = new DefaultHttpServerConnection();
                    conn.bind(socket, _params);

                    // Execute a new worker task to handle the request
                    s_executor.execute(new WorkerTask(_httpService, conn, s_workerCount++));
                } catch (final InterruptedIOException ex) {
                    break;
                } catch (final IOException e) {
                    s_logger.error("I/O error initializing connection thread", e);
                    break;
                }
            }
        }
    }

    static class WorkerTask extends ManagedContextRunnable {
        private final HttpService _httpService;
        private final HttpServerConnection _conn;

        public WorkerTask(final HttpService httpService, final HttpServerConnection conn, final int count) {
            _httpService = httpService;
            _conn = conn;
        }

        @Override
        protected void runInContext() {
            final HttpContext context = new BasicHttpContext(null);
            try {
                while (!Thread.interrupted() && _conn.isOpen()) {
                    _httpService.handleRequest(_conn, context);
                    _conn.close();
                }
            } catch (final ConnectionClosedException ex) {
                if (s_logger.isTraceEnabled()) {
                    s_logger.trace("ApiServer:  Client closed connection");
                }
            } catch (final IOException ex) {
                if (s_logger.isTraceEnabled()) {
                    s_logger.trace("ApiServer:  IOException - " + ex);
                }
            } catch (final HttpException ex) {
                s_logger.warn("ApiServer:  Unrecoverable HTTP protocol violation" + ex);
            } finally {
                try {
                    _conn.shutdown();
                } catch (final IOException ignore) {
                }
            }
        }
    }
}
