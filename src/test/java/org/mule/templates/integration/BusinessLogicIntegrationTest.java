/**
 * Mule Anypoint Template
 * Copyright (c) MuleSoft, Inc.
 * All rights reserved.  http://www.mulesoft.com
 */

package org.mule.templates.integration;

import java.io.FileInputStream;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.math.RandomUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.templates.builders.ObjectBuilder;
import org.mule.templates.db.MySQLDbCreator;

import com.mulesoft.module.batch.BatchTestHelper;

/**
 * The objective of this class is to validate the correct behavior of the flows
 * for this Mule Template that make calls to external systems.
 */
public class BusinessLogicIntegrationTest extends AbstractTemplateTestCase {

	private static Logger LOG = LogManager.getLogger(BusinessLogicIntegrationTest.class);

	private static final String PATH_TO_TEST_PROPERTIES = "./src/test/resources/mule.test.properties";
	private static final String PATH_TO_SQL_SCRIPT = "src/main/resources/user.sql";
	private static final String DATABASE_NAME = "DB2SFDCUserMigration" + new Long(new Date().getTime()).toString();
	private static final MySQLDbCreator DBCREATOR = new MySQLDbCreator(DATABASE_NAME, PATH_TO_SQL_SCRIPT, PATH_TO_TEST_PROPERTIES);

	private String emailUser = null;
	Map<String, Object> user = null;
	private BatchTestHelper helper;

	@Rule
	public DynamicPort port = new DynamicPort("http.port");
	
	@BeforeClass
	public static void init() {
		System.setProperty("database.url", DBCREATOR.getDatabaseUrlWithName());
		DBCREATOR.setUpDatabase();
	}


	@Before
	public void setUp() throws Exception {
		
		final Properties props = new Properties();
		try {
			props.load(new FileInputStream(PATH_TO_TEST_PROPERTIES));
		} catch (Exception e) {
			LOG.error("Error occured while reading mule.test.properties", e);
		}
		emailUser = props.getProperty("existing.user.email");
		
		helper = new BatchTestHelper(muleContext);
		createUsersInDB();
	}

	@After
	public void tearDown() throws Exception {
		deleteUserFromDB();
		DBCREATOR.tearDownDataBase();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testMainFlow() throws Exception {
		runFlow("mainFlow");

		helper.awaitJobTermination(120 * 1000, 500);
		helper.assertJobWasSuccessful();

		final SubflowInterceptingChainLifecycleWrapper subflow = getSubFlow("querySalesforce");
		subflow.initialise();

		final MuleEvent event = subflow.process(getTestEvent(user, MessageExchangePattern.REQUEST_RESPONSE));
		final Map<String, Object> result = (Map<String, Object>) event.getMessage().getPayload();
		LOG.info("querySalesforce result: " + result);

		Assert.assertNotNull(result);
		Assert.assertEquals("There should be matching user in Salesforce now", user.get("email"), result.get("Email"));
	}

	private void createUsersInDB() throws Exception {
		final SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("insertUserDB");
		flow.initialise();
		user = createDbUser();

		final MuleEvent event = flow.process(getTestEvent(user, MessageExchangePattern.REQUEST_RESPONSE));
		final Object result = event.getMessage().getPayload();
		LOG.info("insertUserDB result: " + result);
	}

	private void deleteUserFromDB() throws Exception {
		final SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("deleteUserDB");
		flow.initialise();

		final MuleEvent event = flow.process(getTestEvent(user, MessageExchangePattern.REQUEST_RESPONSE));
		final Object result = event.getMessage().getPayload();
		LOG.info("deleteUserDB result: " + result);
	}

	private Map<String, Object> createDbUser() {
		final String name = "tst" + RandomUtils.nextInt(5);
		return ObjectBuilder.aUser()
				.with("firstname", name)
				.with("lastname", name)
				// set email to existing saleforce user to prevent creating new one
				.with("email", emailUser)
				.build();
	}
}
