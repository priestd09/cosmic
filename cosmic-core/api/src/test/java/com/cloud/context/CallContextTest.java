package com.cloud.context;

import com.cloud.dao.EntityManager;
import com.cloud.legacymodel.user.Account;
import com.cloud.legacymodel.user.User;

import java.util.UUID;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CallContextTest {

    @Mock
    EntityManager entityMgr;

    @Before
    public void setUp() {
        CallContext.init(entityMgr);
        CallContext.register(Mockito.mock(User.class), Mockito.mock(Account.class));
    }

    @After
    public void tearDown() throws Exception {
        CallContext.unregisterAll();
    }

    @Test
    public void testGetContextParameter() {
        final CallContext currentContext = CallContext.current();

        Assert.assertEquals("There is nothing in the context. It should return null", null, currentContext.getContextParameter("key"));
        Assert.assertTrue("There is nothing in the context. The map should be empty", currentContext.getContextParameters().isEmpty());

        final UUID objectUUID = UUID.randomUUID();
        final UUID stringUUID = UUID.randomUUID();

        //Case1: when an entry with the object class is present
        currentContext.putContextParameter(User.class, objectUUID);
        Assert.assertEquals("it should return objectUUID: " + objectUUID, objectUUID, currentContext.getContextParameter(User.class));
        Assert.assertEquals("current context map should have exactly one entry", 1, currentContext.getContextParameters().size());

        //Case2: when an entry with the object class name as String is present
        currentContext.putContextParameter(Account.class.toString(), stringUUID);
        //object is put with key as Account.class.toString but get with key as Account.class
        Assert.assertEquals("it should return stringUUID: " + stringUUID, stringUUID, currentContext.getContextParameter(Account.class));
        Assert.assertEquals("current context map should have exactly two entries", 2, currentContext.getContextParameters().size());

        //Case3: when an entry with both object class and object class name as String is present
        //put an entry of account class object in the context
        currentContext.putContextParameter(Account.class, objectUUID);
        //since both object and string a present in the current context, it should return object value
        Assert.assertEquals("it should return objectUUID: " + objectUUID, objectUUID, currentContext.getContextParameter(Account.class));
        Assert.assertEquals("current context map should have exactly three entries", 3, currentContext.getContextParameters().size());
    }
}
