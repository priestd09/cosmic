package com.cloud.api.agent.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cloud.legacymodel.communication.command.ResizeVolumeCommand;
import com.cloud.legacymodel.communication.answer.CheckNetworkAnswer;
import com.cloud.legacymodel.communication.command.CheckNetworkCommand;
import com.cloud.legacymodel.storage.StoragePool;
import com.cloud.legacymodel.to.StorageFilerTO;
import com.cloud.model.enumeration.HypervisorType;
import com.cloud.model.enumeration.StoragePoolStatus;
import com.cloud.model.enumeration.StoragePoolType;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class CheckNetworkAnswerTest {
    CheckNetworkCommand cnc;
    CheckNetworkAnswer cna;

    @Before
    public void setUp() {
        cnc = Mockito.mock(CheckNetworkCommand.class);
        cna = new CheckNetworkAnswer(cnc, true, "details", true);
    }

    @Test
    public void testGetResult() {
        final boolean b = cna.getResult();
        assertTrue(b);
    }

    @Test
    public void testGetDetails() {
        final String d = cna.getDetails();
        assertTrue(d.equals("details"));
    }

    @Test
    public void testNeedReconnect() {
        final boolean b = cna.needReconnect();
        assertTrue(b);
    }

    @Test
    public void testExecuteInSequence() {
        final boolean b = cna.executeInSequence();
        assertFalse(b);
    }

    public static class ResizeVolumeCommandTest {

        public StoragePool dummypool = new StoragePool() {
            @Override
            public long getId() {
                return 1L;
            }

            @Override
            public String getName() {
                return "name";
            }

            @Override
            public StoragePoolType getPoolType() {
                return StoragePoolType.Filesystem;
            }

            @Override
            public Date getCreated() {
                Date date = null;
                try {
                    date = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss").parse("01/01/1970 12:12:12");
                } catch (final ParseException e) {
                    e.printStackTrace();
                }
                return date;
            }

            @Override
            public Date getUpdateTime() {
                return new Date();
            }

            @Override
            public long getDataCenterId() {
                return 0L;
            }

            @Override
            public long getCapacityBytes() {
                return 0L;
            }

            @Override
            public long getUsedBytes() {
                return 0L;
            }

            @Override
            public Long getCapacityIops() {
                return 0L;
            }

            @Override
            public Long getClusterId() {
                return 0L;
            }

            @Override
            public String getHostAddress() {
                return "hostAddress";
            }

            @Override
            public String getPath() {
                return "path";
            }

            @Override
            public String getUserInfo() {
                return "userInfo";
            }

            @Override
            public boolean isShared() {
                return false;
            }

            @Override
            public boolean isLocal() {
                return false;
            }

            @Override
            public StoragePoolStatus getStatus() {
                return StoragePoolStatus.Up;
            }

            @Override
            public int getPort() {
                return 25;
            }

            @Override
            public Long getPodId() {
                return 0L;
            }

            @Override
            public String getStorageProviderName() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public boolean isInMaintenance() {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public HypervisorType getHypervisor() {
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public String getUuid() {
                return "bed9f83e-cac3-11e1-ac8a-0050568b007e";
            }
        };

        Long newSize = 4194304L;
        Long currentSize = 1048576L;

        ResizeVolumeCommand rv = new ResizeVolumeCommand("dummydiskpath", new StorageFilerTO(dummypool), currentSize, newSize, false, "vmName");

        @Test
        public void testExecuteInSequence() {
            final boolean b = rv.executeInSequence();
            assertFalse(b);
        }

        @Test
        public void testGetPath() {
            final String path = rv.getPath();
            assertTrue(path.equals("dummydiskpath"));
        }

        @Test
        public void testGetPoolUuid() {
            final String poolUuid = rv.getPoolUuid();
            assertTrue(poolUuid.equals("bed9f83e-cac3-11e1-ac8a-0050568b007e"));
        }

        @Test
        public void testGetPool() {
            final StorageFilerTO pool = rv.getPool();

            final Long id = pool.getId();
            final Long expectedL = 1L;
            assertEquals(expectedL, id);

            final String uuid = pool.getUuid();
            assertTrue(uuid.equals("bed9f83e-cac3-11e1-ac8a-0050568b007e"));

            final String host = pool.getHost();
            assertTrue(host.equals("hostAddress"));

            final String path = pool.getPath();
            assertTrue(path.equals("path"));

            final String userInfo = pool.getUserInfo();
            assertTrue(userInfo.equals("userInfo"));

            final Integer port = pool.getPort();
            final Integer expectedI = 25;
            assertEquals(expectedI, port);

            final StoragePoolType type = pool.getType();
            assertEquals(StoragePoolType.Filesystem, type);

            final String str = pool.toString();
            assertTrue(str.equals("Pool[" + id.toString() + "|" + host + ":" + port.toString() + "|" + path + "]"));
        }

        @Test
        public void testGetNewSize() {
            final long newSize = rv.getNewSize();
            assertTrue(newSize == 4194304L);
        }

        @Test
        public void testGetCurrentSize() {
            final long currentSize = rv.getCurrentSize();
            assertTrue(currentSize == 1048576L);
        }

        @Test
        public void testGetShrinkOk() {
            assertFalse(rv.getShrinkOk());
        }

        @Test
        public void testGetInstanceName() {
            final String vmName = rv.getInstanceName();
            assertTrue(vmName.equals("vmName"));
        }
    }
}
