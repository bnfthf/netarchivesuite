/*
 * #%L
 * Netarchivesuite - harvester - test
 * %%
 * Copyright (C) 2005 - 2014 The Royal Danish Library, the Danish State and University Library,
 *             the National Library of France and the Austrian National Library.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

package dk.netarkivet.harvester.harvesting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.exceptions.IOFailure;
import dk.netarkivet.common.utils.FileUtils;
import dk.netarkivet.common.utils.Settings;
import dk.netarkivet.harvester.HarvesterSettings;
import dk.netarkivet.harvester.HeritrixConfigurator;
import dk.netarkivet.harvester.harvesting.controller.BnfHeritrixController;
import dk.netarkivet.harvester.harvesting.controller.HeritrixController;
import dk.netarkivet.harvester.harvesting.controller.HeritrixControllerFactory;
import dk.netarkivet.testutils.preconfigured.MoveTestFiles;

/**
 * Unittest for the HeritrixControllerFactory class.
 */
@SuppressWarnings({"unused"})
public class HeritrixControllerFactoryTester {

    private MoveTestFiles mtf;
    private File dummyLuceneIndex;
    private String defaultController;

    public HeritrixControllerFactoryTester() {
        mtf = new MoveTestFiles(Heritrix1ControllerTestInfo.CRAWLDIR_ORIGINALS_DIR, Heritrix1ControllerTestInfo.WORKING_DIR);
    }

    @Before
    public void setUp() throws IOException {
        HeritrixConfigurator.setUseH1();
        mtf.setUp();
        dummyLuceneIndex = mtf.newTmpDir();
        // Out commented to avoid reference to archive module from harvester module.
        // LuceneUtils.makeDummyIndex(dummyLuceneIndex);
        defaultController = Settings.get(HarvesterSettings.HERITRIX_CONTROLLER_CLASS);
    }

    @After
    public void tearDown() {
        mtf.tearDown();
        Settings.set(HarvesterSettings.HERITRIX_CONTROLLER_CLASS, defaultController);
    }

    /**
     * Test that we can construct a HeritrixController using the setting in settings.xml
     */
    @Test
    @Ignore("Heritrix launcher code non functional")
    public void testGetDefaultHeritrixControllerDefaultSettings() {
        File origSeeds = Heritrix1ControllerTestInfo.SEEDS_FILE;
        File crawlDir = Heritrix1ControllerTestInfo.HERITRIX_TEMP_DIR;
        crawlDir.mkdirs();
        File orderXml = new File(crawlDir, "order.xml");
        File seedsTxt = new File(crawlDir, "seeds.txt");
        FileUtils.copyFile(Heritrix1ControllerTestInfo.ORDER_FILE, orderXml);
        FileUtils.copyFile(origSeeds, seedsTxt);
        // FIXME HeritrixFiles Hardwired
        HeritrixFiles files = HeritrixFiles.getH1HeritrixFilesWithDefaultJmxFiles(
        		crawlDir, new JobInfoTestImpl(Long.parseLong(Heritrix1ControllerTestInfo.ARC_JOB_ID),
                Long.parseLong(Heritrix1ControllerTestInfo.ARC_HARVEST_ID)));
        HeritrixController hc = HeritrixControllerFactory.getDefaultHeritrixController(files);
        assertTrue("Should have got a JMXHeritricController, not " + hc, hc instanceof BnfHeritrixController);
    }

    /**
     * Test that we can change the implementation of HeritrixController to construct
     */
    @Test
    public void testGetDefaultHeritrixControllerChangeDefaultSettigs() {
        final String controllerClass = "dk.netarkivet.harvester.harvesting.HeritrixControllerFactoryTester$DummyHeritrixController";
        Settings.set(HarvesterSettings.HERITRIX_CONTROLLER_CLASS, controllerClass);
        HeritrixController hc = HeritrixControllerFactory.getDefaultHeritrixController("hello world");
        assertTrue("Should have got a DummyHeritrixController, not " + hc, hc instanceof DummyHeritrixController);
    }

    /**
     * Test we throw the expected exception when the signature is invalid
     */
    @Test
    public void testGetDefaultHeritrixControllerWrongSignature() {
        Settings.set(HarvesterSettings.HERITRIX_CONTROLLER_CLASS,
                "dk.netarkivet.harvester.harvesting.HeritrixControllerFactoryTester$DummyHeritrixController");
        try {
            HeritrixController hc = HeritrixControllerFactory
                    .getDefaultHeritrixController("hello world", "hello world");
            fail("Expected to throw ArgumentNotValid on invlaid signature");
        } catch (ArgumentNotValid e) {
            // expected
        }
    }

    public static class DummyHeritrixController implements HeritrixController {

        public DummyHeritrixController(String dummyArg) {
            System.out.println(DummyHeritrixController.class);
            // Just a dummy constructor for testing
        }

        public void initialize() {
            // TODO: implement method
            throw new RuntimeException("Not implemented");
        }

        public void requestCrawlStart() throws IOFailure {
            // TODO: implement method
            throw new RuntimeException("Not implemented");
        }

        public void beginCrawlStop() {
            // TODO: implement method
            throw new RuntimeException("Not implemented");
        }

        public void requestCrawlStop(String reason) {
            // TODO: implement method
            throw new RuntimeException("Not implemented");
        }

        public boolean atFinish() {
            // TODO: implement method
            throw new RuntimeException("Not implemented");
        }

        public boolean crawlIsEnded() {
            // TODO: implement method
            throw new RuntimeException("Not implemented");
        }

        public int getActiveToeCount() {
            // TODO: implement method
            throw new RuntimeException("Not implemented");
        }

        public long getQueuedUriCount() {
            // TODO: implement method
            throw new RuntimeException("Not implemented");
        }

        public int getCurrentProcessedKBPerSec() {
            // TODO: implement method
            throw new RuntimeException("Not implemented");
        }

        public String getProgressStats() {
            // TODO: implement method
            throw new RuntimeException("Not implemented");
        }

        public boolean isPaused() {
            // TODO: implement method
            throw new RuntimeException("Not implemented");
        }

        public void cleanup() {
            // TODO: implement method
            throw new RuntimeException("Not implemented");
        }

        public String getHarvestInformation() {
            // TODO: implement method
            throw new RuntimeException("Not implemented");
        }
    }

}
