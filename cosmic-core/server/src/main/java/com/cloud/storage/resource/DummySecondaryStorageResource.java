package com.cloud.storage.resource;

import com.cloud.common.resource.ServerResource;
import com.cloud.common.resource.ServerResourceBase;
import com.cloud.common.storageprocessor.TemplateConstants;
import com.cloud.legacymodel.communication.answer.Answer;
import com.cloud.legacymodel.communication.answer.CheckHealthAnswer;
import com.cloud.legacymodel.communication.answer.DownloadAnswer;
import com.cloud.legacymodel.communication.answer.GetStorageStatsAnswer;
import com.cloud.legacymodel.communication.answer.ReadyAnswer;
import com.cloud.legacymodel.communication.command.CheckHealthCommand;
import com.cloud.legacymodel.communication.command.Command;
import com.cloud.legacymodel.communication.command.DownloadCommand;
import com.cloud.legacymodel.communication.command.DownloadProgressCommand;
import com.cloud.legacymodel.communication.command.GetStorageStatsCommand;
import com.cloud.legacymodel.communication.command.PingCommand;
import com.cloud.legacymodel.communication.command.PingStorageCommand;
import com.cloud.legacymodel.communication.command.ReadyCommand;
import com.cloud.legacymodel.communication.command.StartupCommand;
import com.cloud.legacymodel.communication.command.StartupStorageCommand;
import com.cloud.legacymodel.storage.TemplateProp;
import com.cloud.legacymodel.storage.VMTemplateStatus;
import com.cloud.model.enumeration.HostType;
import com.cloud.model.enumeration.StoragePoolType;
import com.cloud.model.enumeration.StorageResourceType;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.dao.VMTemplateDao;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DummySecondaryStorageResource extends ServerResourceBase implements ServerResource {
    private static final Logger s_logger = LoggerFactory.getLogger(DummySecondaryStorageResource.class);

    String _dc;
    String _pod;
    String _guid;
    String _dummyPath;
    @Inject
    VMTemplateDao _tmpltDao;
    private boolean _useServiceVm;

    public DummySecondaryStorageResource() {
        setUseServiceVm(true);
    }

    public void setUseServiceVm(final boolean useServiceVm) {
        this._useServiceVm = useServiceVm;
    }

    public DummySecondaryStorageResource(final boolean useServiceVM) {
        setUseServiceVm(useServiceVM);
    }

    @Override
    public HostType getType() {
        return HostType.SecondaryStorage;
    }

    @Override
    public StartupCommand[] initialize() {
        final StartupStorageCommand cmd =
                new StartupStorageCommand("dummy", StoragePoolType.NetworkFilesystem, 1024 * 1024 * 1024 * 100L, new HashMap<>());

        cmd.setResourceType(StorageResourceType.SECONDARY_STORAGE);
        cmd.setIqn(null);
        cmd.setNfsShare(this._guid);

        fillNetworkInformation(cmd);
        cmd.setDataCenter(this._dc);
        cmd.setPod(this._pod);
        cmd.setGuid(this._guid);

        cmd.setName(this._guid);
        cmd.setVersion(DummySecondaryStorageResource.class.getPackage().getImplementationVersion());
        /* gather TemplateInfo in second storage */
        cmd.setTemplateInfo(getDefaultSystemVmTemplateInfo());
        cmd.getHostDetails().put("mount.parent", "dummy");
        cmd.getHostDetails().put("mount.path", "dummy");
        cmd.getHostDetails().put("orig.url", this._guid);

        final String[] tok = this._dummyPath.split(":");
        cmd.setPrivateIpAddress(tok[0]);
        return new StartupCommand[]{cmd};
    }

    @Override
    public PingCommand getCurrentStatus(final long id) {
        return new PingStorageCommand(HostType.Storage, id, new HashMap<>());
    }

    @Override
    public Answer executeRequest(final Command cmd) {
        if (cmd instanceof DownloadProgressCommand) {
            return new DownloadAnswer(null, 100, cmd, VMTemplateStatus.DOWNLOADED, "dummyFS", "/dummy");
        } else if (cmd instanceof DownloadCommand) {
            return new DownloadAnswer(null, 100, cmd, VMTemplateStatus.DOWNLOADED, "dummyFS", "/dummy");
        } else if (cmd instanceof GetStorageStatsCommand) {
            return execute((GetStorageStatsCommand) cmd);
        } else if (cmd instanceof CheckHealthCommand) {
            return new CheckHealthAnswer((CheckHealthCommand) cmd, true);
        } else if (cmd instanceof ReadyCommand) {
            return new ReadyAnswer((ReadyCommand) cmd);
        } else {
            return Answer.createUnsupportedCommandAnswer(cmd);
        }
    }

    protected GetStorageStatsAnswer execute(final GetStorageStatsCommand cmd) {
        final long size = 1024 * 1024 * 1024 * 100L;
        return new GetStorageStatsAnswer(cmd, 0, size);
    }

    public Map<String, TemplateProp> getDefaultSystemVmTemplateInfo() {
        final List<VMTemplateVO> tmplts = this._tmpltDao.listAllSystemVMTemplates();
        final Map<String, TemplateProp> tmpltInfo = new HashMap<>();
        if (tmplts != null) {
            for (final VMTemplateVO tmplt : tmplts) {
                final TemplateProp routingInfo =
                        new TemplateProp(tmplt.getUniqueName(), TemplateConstants.DEFAULT_SYSTEM_VM_TEMPLATE_PATH + tmplt.getId() + File.separator, false, false);
                tmpltInfo.put(tmplt.getUniqueName(), routingInfo);
            }
        }
        return tmpltInfo;
    }

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);

        this._guid = (String) params.get("guid");
        if (this._guid == null) {
            throw new ConfigurationException("Unable to find the guid");
        }

        this._dc = (String) params.get("zone");
        if (this._dc == null) {
            throw new ConfigurationException("Unable to find the zone");
        }
        this._pod = (String) params.get("pod");

        this._dummyPath = (String) params.get("mount.path");
        if (this._dummyPath == null) {
            throw new ConfigurationException("Unable to find mount.path");
        }

        return true;
    }

    @Override
    protected String getDefaultScriptsDir() {
        return "dummy";
    }

    public boolean useServiceVm() {
        return this._useServiceVm;
    }

    @Override
    public void setName(final String name) {
        // TODO Auto-generated method stub

    }

    @Override
    public Map<String, Object> getConfigParams() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setConfigParams(final Map<String, Object> params) {
        // TODO Auto-generated method stub

    }

    @Override
    public int getRunLevel() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setRunLevel(final int level) {
        // TODO Auto-generated method stub

    }
}
