package com.cloud.agent.resource.kvm.wrapper;

import com.cloud.agent.resource.kvm.LibvirtComputingResource;
import com.cloud.common.request.CommandWrapper;
import com.cloud.common.request.ResourceWrapper;
import com.cloud.legacymodel.communication.answer.Answer;
import com.cloud.legacymodel.communication.command.ModifySshKeysCommand;
import com.cloud.utils.StringUtils;
import com.cloud.utils.script.Script;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ResourceWrapper(handles = ModifySshKeysCommand.class)
public final class LibvirtModifySshKeysCommandWrapper
        extends CommandWrapper<ModifySshKeysCommand, Answer, LibvirtComputingResource> {

    private static final Logger s_logger = LoggerFactory.getLogger(LibvirtModifySshKeysCommandWrapper.class);

    @Override
    public Answer execute(final ModifySshKeysCommand command, final LibvirtComputingResource libvirtComputingResource) {

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = libvirtComputingResource.getLibvirtUtilitiesHelper();

        final String sshkeyspath = libvirtUtilitiesHelper.retrieveSshKeysPath();
        final String sshpubkeypath = libvirtUtilitiesHelper.retrieveSshPubKeyPath();
        final String sshprvkeypath = libvirtUtilitiesHelper.retrieveSshPrvKeyPath();

        final File sshKeysDir = new File(sshkeyspath);
        String result = null;
        if (!sshKeysDir.exists()) {
            // Change permissions for the 700
            final Script script = new Script("mkdir", libvirtComputingResource.getScriptsTimeout(), s_logger);
            script.add("-m", "700");
            script.add(sshkeyspath);
            script.execute();

            if (!sshKeysDir.exists()) {
                s_logger.debug("failed to create directory " + sshkeyspath);
            }
        }

        final File pubKeyFile = new File(sshpubkeypath);
        if (!pubKeyFile.exists()) {
            try {
                pubKeyFile.createNewFile();
            } catch (final IOException e) {
                result = "Failed to create file: " + e.toString();
                s_logger.debug(result);
            }
        }

        if (pubKeyFile.exists()) {
            try (final FileOutputStream pubkStream = new FileOutputStream(pubKeyFile)) {
                pubkStream.write(command.getPubKey().getBytes(StringUtils.getPreferredCharset()));
            } catch (final FileNotFoundException e) {
                result = "File" + sshpubkeypath + "is not found:"
                        + e.toString();
                s_logger.debug(result);
            } catch (final IOException e) {
                result = "Write file " + sshpubkeypath + ":" + e.toString();
                s_logger.debug(result);
            }
        }

        final File prvKeyFile = new File(sshprvkeypath);
        if (!prvKeyFile.exists()) {
            try {
                prvKeyFile.createNewFile();
            } catch (final IOException e) {
                result = "Failed to create file: " + e.toString();
                s_logger.debug(result);
            }
        }

        if (prvKeyFile.exists()) {
            final String prvKey = command.getPrvKey();
            try (final FileOutputStream prvKStream = new FileOutputStream(prvKeyFile)) {
                if (prvKStream != null) {
                    prvKStream.write(prvKey.getBytes(StringUtils.getPreferredCharset()));
                }
            } catch (final FileNotFoundException e) {
                result = "File" + sshprvkeypath + "is not found:" + e.toString();
                s_logger.debug(result);
            } catch (final IOException e) {
                result = "Write file " + sshprvkeypath + ":" + e.toString();
                s_logger.debug(result);
            }
            final Script script = new Script("chmod", libvirtComputingResource.getScriptsTimeout(), s_logger);
            script.add("600", sshprvkeypath);
            script.execute();
        }

        if (result != null) {
            return new Answer(command, false, result);
        } else {
            return new Answer(command, true, null);
        }
    }
}
