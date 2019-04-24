/*
 * Copyright (c)  2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.extension.siddhi.script.scala;

import io.siddhi.core.SiddhiAppRuntime;
import io.siddhi.core.SiddhiManager;
import io.siddhi.core.event.Event;
import io.siddhi.core.exception.SiddhiAppCreationException;
import io.siddhi.core.query.output.callback.QueryCallback;
import io.siddhi.core.stream.input.InputHandler;
import io.siddhi.core.util.EventPrinter;
import io.siddhi.query.api.SiddhiApp;
import io.siddhi.query.api.definition.FunctionDefinition;
import io.siddhi.query.api.exception.DuplicateDefinitionException;
import io.siddhi.query.api.exception.SiddhiAppValidationException;
import io.siddhi.query.compiler.exception.SiddhiParserException;
import org.apache.log4j.Logger;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.extension.siddhi.script.js.EvalJavaScript;
import org.wso2.extension.siddhi.script.scala.test.util.SiddhiTestHelper;

import java.util.concurrent.atomic.AtomicInteger;

public class EvalScriptTestCase {

    private static final Logger log = Logger.getLogger(EvalScriptTestCase.class);
    private AtomicInteger count = new AtomicInteger(0);

    @BeforeMethod
    public void init() {
        count.set(0);
    }

    @Test
    public void testEvalScalaConcat() throws InterruptedException {

        log.info("TestEvalScalaConcat");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("script:javascript", EvalJavaScript.class);
        siddhiManager.setExtension("script:scala", EvalScala.class);

        String concatFunc = "define function concatS[Scala] return string {\n" +
                "  var concatenatedString = \"\"\n" +
                "  for(i <- 0 until data.length){\n" +
                "  concatenatedString += data(i).toString\n" +
                "  }\n" +
                "  concatenatedString\n" +
                "};";
        //siddhiManager.defineFunction(concatFunc);
        String cseEventStream = "define stream cseEventStream (symbol string, price float, volume long);";
        String query = ("@info(name = 'query1') from cseEventStream select price ," +
                " concatS(symbol,' ',price) as concatStr " +
                "group by volume insert into mailOutput;");
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(
                concatFunc + cseEventStream + query);

        siddhiAppRuntime.addCallback("query1", new QueryCallback() {
            @Override
            public void receive(long timeStamp, Event[] inEvents, Event[] removeEvents) {
                EventPrinter.print(timeStamp, inEvents, removeEvents);
                Object value = inEvents[inEvents.length - 1].getData(1);
                AssertJUnit.assertEquals("IBM 700.0", value);
                count.incrementAndGet();
            }
        });

        InputHandler inputHandler = siddhiAppRuntime.getInputHandler("cseEventStream");
        siddhiAppRuntime.start();
        inputHandler.send(new Object[]{"IBM", 700f, 100L});
        SiddhiTestHelper.waitForEvents(100, 1, count, 60000);
        AssertJUnit.assertEquals(1, count.get());

        siddhiAppRuntime.shutdown();
    }

    @Test
    public void testEvalJavaScriptConcat() throws InterruptedException {

        log.info("testEvalJavaScriptConcat");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("script:javascript", EvalJavaScript.class);
        siddhiManager.setExtension("script:scala", EvalScala.class);

        String concatFunc = "define function concatJ[JavaScript] return string {\n" +
                "  var str1 = data[0];\n" +
                "  var str2 = data[1];\n" +
                "  var str3 = data[2];\n" +
                "  var res = str1.concat(str2,str3);\n" +
                "  return res;\n" +
                "};";

        String cseEventStream = "define stream cseEventStream (symbol string, price float, volume long);";
        String query = ("@info(name = 'query1') from cseEventStream select price , " +
                "concatJ(symbol,' ',price) as concatStr " +
                "group by volume insert into mailOutput;");
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(
                concatFunc + cseEventStream + query);

        siddhiAppRuntime.addCallback("query1", new QueryCallback() {
            @Override
            public void receive(long timeStamp, Event[] inEvents, Event[] removeEvents) {
                EventPrinter.print(timeStamp, inEvents, removeEvents);
                Object value = inEvents[inEvents.length - 1].getData(1);
                AssertJUnit.assertEquals("WSO2 50", value);
                count.incrementAndGet();
            }
        });

        InputHandler inputHandler = siddhiAppRuntime.getInputHandler("cseEventStream");
        siddhiAppRuntime.start();

        inputHandler.send(new Object[]{"WSO2", 50f, 60f, 60L, 6});
        SiddhiTestHelper.waitForEvents(100, 1, count, 60000);
        AssertJUnit.assertEquals(1, count.get());

        siddhiAppRuntime.shutdown();
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class)
    public void testScalaCompilationFailure() throws InterruptedException {

        log.info("testScalaCompilationFailure");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("script:javascript", EvalJavaScript.class);
        siddhiManager.setExtension("script:scala", EvalScala.class);

        String concatFunc = "define function concat[Scala] return string {\n" +
                "  for(i <- 0 until data.length){\n" +
                "  concatenatedString += data(i).toString\n" +
                "  }\n" +
                "  concatenatedString\n" +
                "}";

        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(concatFunc);

        siddhiAppRuntime.shutdown();
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class)
    public void testJavaScriptCompilationFailure() throws InterruptedException {

        log.info("testJavaScriptCompilationFailure");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("script:javascript", EvalJavaScript.class);
        siddhiManager.setExtension("script:scala", EvalScala.class);

        String concatFunc = "define function concatJ[JavaScript] return string {\n" +
                "  var str1 = data[0;\n" +
                "  var str2 = data[1];\n" +
                "  var str3 = data[2];\n" +
                "  var res = str1.concat(str2,str3);\n" +
                "  return res;\n" +
                "};";

        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(concatFunc);

        siddhiAppRuntime.shutdown();
    }

    @Test(expectedExceptions = DuplicateDefinitionException.class)
    public void testDefineFunctionsWithSameFunctionID() throws InterruptedException {

        log.info("testDefineFunctionsWithSameFunctionID");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("script:javascript", EvalJavaScript.class);
        siddhiManager.setExtension("script:scala", EvalScala.class);

        String concatFunc1 =
                "define function concat[Scala] return string {\n" +
                        "  var concatenatedString = \"\"\n" +
                        "  for(i <- 0 until data.length) {\n" +
                        "     concatenatedString += data(i).toString\n" +
                        "  }\n" +
                        "  concatenatedString\n" +
                        "};";

        String concatFunc2 =
                "define function concat[JavaScript] return string {\n" +
                        "  var str1 = data[0];\n" +
                        "  var str2 = data[1];\n" +
                        "  var str3 = data[2];\n" +
                        "  var res = str1.concat(str2,str3);\n" +
                        "  return res;\n" +
                        "};\n";
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(
                concatFunc1 + concatFunc2);
        siddhiAppRuntime.shutdown();
    }

    @Test
    public void testDefineManyFunctionsAndCallThemRandom() throws InterruptedException {

        log.info("testDefineManyFunctionsAndCallThemRandom");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("script:javascript", EvalJavaScript.class);
        siddhiManager.setExtension("script:scala", EvalScala.class);

        String concatSFunc =
                "define function concatS[Scala] return string {\n" +
                        "  var concatenatedString = \"\"\n" +
                        "  for(i <- 0 until data.length){\n" +
                        "     concatenatedString += data(i).toString\n" +
                        "  }\n" +
                        "  concatenatedString\n" +
                        "};\n";

        String concatJFunc =
                "define function concatJ[JavaScript] return string {\n" +
                        "   var str1 = data[0].toString();\n" +
                        "   var str2 = data[1].toString();\n" +
                        "   var str3 = data[2].toString();\n" +
                        "   var res = str1.concat(str2,str3);\n" +
                        "   return res;\n" +
                        "};\n";

        String toFloatSFunc =
                "define function toFloatS[Scala] return float {\n" +
                        "   data(0).asInstanceOf[Long].toFloat\n" +
                        "};\n";

        String toStringJFunc =
                "define function toStringJ[JavaScript] return string {\n" +
                        "   return data[0].toString();\n" +
                        "};\n";

        String cseEventStream = "define stream cseEventStream (symbol string, price float, volume long);\n";
        String query1 = ("@info(name = 'query1') from cseEventStream select price , " +
                "toStringJ(price) as concatStr insert into mailto1;\n");
        String query2 = ("@info(name = 'query2') from cseEventStream select price ," +
                " toFloatS(volume) as concatStr insert into mailto2;\n");
        String query3 = ("@info(name = 'query3') from cseEventStream select price ," +
                " concatJ(symbol,' ',price) as concatStr insert into mailto3;\n");
        String query4 = ("@info(name = 'query4') from cseEventStream select price ," +
                " concatS(symbol,' ',price) as concatStr insert into mailto4;\n");
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(concatSFunc +
                concatJFunc + toFloatSFunc + toStringJFunc + cseEventStream + query1 + query2 + query3 + query4);

        siddhiAppRuntime.addCallback("query1", new QueryCallback() {
            @Override
            public void receive(long timeStamp, Event[] inEvents, Event[] removeEvents) {
                EventPrinter.print(timeStamp, inEvents, removeEvents);
                Object value = inEvents[inEvents.length - 1].getData(1);
                AssertJUnit.assertEquals("50", value);
                count.incrementAndGet();
            }
        });

        siddhiAppRuntime.addCallback("query2", new QueryCallback() {
            @Override
            public void receive(long timeStamp, Event[] inEvents, Event[] removeEvents) {
                EventPrinter.print(timeStamp, inEvents, removeEvents);
                Object value = inEvents[inEvents.length - 1].getData(1);
                AssertJUnit.assertEquals(6f, value);
                count.incrementAndGet();
            }
        });

        siddhiAppRuntime.addCallback("query3", new QueryCallback() {
            @Override
            public void receive(long timeStamp, Event[] inEvents, Event[] removeEvents) {
                EventPrinter.print(timeStamp, inEvents, removeEvents);
                Object value = inEvents[inEvents.length - 1].getData(1);
                AssertJUnit.assertEquals("WSO2 50", value);
                count.incrementAndGet();
            }
        });

        siddhiAppRuntime.addCallback("query4", new QueryCallback() {
            @Override
            public void receive(long timeStamp, Event[] inEvents, Event[] removeEvents) {
                EventPrinter.print(timeStamp, inEvents, removeEvents);
                Object value = inEvents[inEvents.length - 1].getData(1);
                AssertJUnit.assertEquals("WSO2 50.0", value);
                count.incrementAndGet();
            }
        });

        InputHandler inputHandler = siddhiAppRuntime.getInputHandler("cseEventStream");
        siddhiAppRuntime.start();
        inputHandler.send(new Object[]{"WSO2", 50f, 6L});

        SiddhiTestHelper.waitForEvents(1000, 4, count, 60000);
        AssertJUnit.assertEquals(4, count.get());

        siddhiAppRuntime.shutdown();
    }

    @Test
    public void testReturnType() throws InterruptedException {

        log.info("testReturnType");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("script:javascript", EvalJavaScript.class);
        siddhiManager.setExtension("script:scala", EvalScala.class);

        String toFloatSFunc =
                "define function toFloatS[Scala] return float {\n" +
                        "   data(0).asInstanceOf[String].toFloat\n" +
                        "};\n";

        String cseEventStream = "define stream cseEventStream (symbol string, price string, volume long);\n";

        String query1 = ("@info(name = 'query1') from cseEventStream select price ," +
                " toFloatS(price) as priceF insert into mailto1;\n");
        String query2 = ("@info(name = 'query2') from mailto1 select priceF/2 as newPrice insert into mailto2;\n");

        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(
                toFloatSFunc + cseEventStream + query1 + query2);

        siddhiAppRuntime.addCallback("query2", new QueryCallback() {
            @Override
            public void receive(long timeStamp, Event[] inEvents, Event[] removeEvents) {
                EventPrinter.print(timeStamp, inEvents, removeEvents);
                Object value = inEvents[inEvents.length - 1].getData(0);
                AssertJUnit.assertEquals(25.0f, value);
                count.incrementAndGet();
            }
        });

        InputHandler inputHandler = siddhiAppRuntime.getInputHandler("cseEventStream");
        siddhiAppRuntime.start();
        inputHandler.send(new Object[]{"WSO2", "50.0", 60f, 60L, 6});

        SiddhiTestHelper.waitForEvents(1000, 1, count, 60000);
        AssertJUnit.assertEquals(1, count.get());

        siddhiAppRuntime.shutdown();
    }

    @Test(expectedExceptions = SiddhiAppValidationException.class)
    public void testMissingReturnType() {

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("script:javascript", EvalJavaScript.class);
        siddhiManager.setExtension("script:scala", EvalScala.class);

        SiddhiApp.siddhiApp("test").defineFunction((new FunctionDefinition().id("concat").language("Scala").body(
                "var concatenatedString = \"\"\n" +
                        "for(i <- 0 until data.length){\n" +
                        "  concatenatedString += data(i).toString\n" +
                        "}\n"
                        + "concatenatedString")));
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class)
    public void testUseUndefinedFunction() throws InterruptedException {
        log.info("testUseUndefinedFunction");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("script:javascript", EvalJavaScript.class);
        siddhiManager.setExtension("script:scala", EvalScala.class);
        //siddhiManager.defineFunction(concatFunc);
        String cseEventStream = "define stream cseEventStream (symbol string, price float, volume long);";
        String query = ("@info(name = 'query1') from cseEventStream select price ," +
                " undefinedFunc(symbol,' ',price) as concatStr " +
                "group by volume insert into mailOutput;");
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(cseEventStream + query);

        siddhiAppRuntime.addCallback("query1", new QueryCallback() {
            @Override
            public void receive(long timeStamp, Event[] inEvents, Event[] removeEvents) {
                EventPrinter.print(timeStamp, inEvents, removeEvents);
                Object value = inEvents[inEvents.length - 1].getData(1);
                AssertJUnit.assertEquals("IBM 700.0", value);
                count.incrementAndGet();
            }
        });

        InputHandler inputHandler = siddhiAppRuntime.getInputHandler("cseEventStream");
        siddhiAppRuntime.start();
        inputHandler.send(new Object[]{"IBM", 700f, 100L});
        SiddhiTestHelper.waitForEvents(1000, 1, count, 60000);
        AssertJUnit.assertEquals(1, count.get());

        siddhiAppRuntime.shutdown();
    }

    @Test(expectedExceptions = SiddhiParserException.class)
    public void testMissingFunctionKeyWord() throws InterruptedException {
        log.info("testDefineManyFunctionsAndCallThemRandom");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("script:javascript", EvalJavaScript.class);
        siddhiManager.setExtension("script:scala", EvalScala.class);

        String concatFunc1 =
                "define concat[Scala] return string {\n" +
                        "  var concatenatedString = \"\"\n" +
                        "  for(i <- 0 until data.length) {\n" +
                        "     concatenatedString += data(i).toString\n" +
                        "  }\n" +
                        "  concatenatedString\n" +
                        "};";
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(concatFunc1);
        siddhiAppRuntime.shutdown();
    }

    @Test(expectedExceptions = SiddhiParserException.class)
    public void testMissingDefineKeyWord() throws InterruptedException {
        log.info("testDefineManyFunctionsAndCallThemRandom");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("script:javascript", EvalJavaScript.class);
        siddhiManager.setExtension("script:scala", EvalScala.class);

        String concatFunc1 =
                "function concat[Scala] return string {\n" +
                        "  var concatenatedString = \"\"\n" +
                        "  for(i <- 0 until data.length) {\n" +
                        "     concatenatedString += data(i).toString\n" +
                        "  }\n" +
                        "  concatenatedString\n" +
                        "};";
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(concatFunc1);
        siddhiAppRuntime.shutdown();
    }

    @Test(expectedExceptions = SiddhiParserException.class)
    public void testMissingFunctionName() throws InterruptedException {
        log.info("testDefineManyFunctionsAndCallThemRandom");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("script:javascript", EvalJavaScript.class);
        siddhiManager.setExtension("script:scala", EvalScala.class);

        String concatFunc1 =
                "define function [Scala] return string {\n" +
                        "  var concatenatedString = \"\"\n" +
                        "  for(i <- 0 until data.length) {\n" +
                        "     concatenatedString += data(i).toString\n" +
                        "  }\n" +
                        "  concatenatedString\n" +
                        "};";
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(concatFunc1);
        siddhiAppRuntime.shutdown();
    }

    @Test(expectedExceptions = SiddhiParserException.class)
    public void testMissingLanguage() throws InterruptedException {
        log.info("testDefineManyFunctionsAndCallThemRandom");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("script:javascript", EvalJavaScript.class);
        siddhiManager.setExtension("script:scala", EvalScala.class);

        String concatFunc1 =
                "define function concat[] return string {\n" +
                        "  var concatenatedString = \"\"\n" +
                        "  for(i <- 0 until data.length) {\n" +
                        "     concatenatedString += data(i).toString\n" +
                        "  }\n" +
                        "  concatenatedString\n" +
                        "};";
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(concatFunc1);
        siddhiAppRuntime.shutdown();
    }

    @Test(expectedExceptions = SiddhiParserException.class)
    public void testMissingBrackets() throws InterruptedException {
        log.info("testDefineManyFunctionsAndCallThemRandom");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("script:javascript", EvalJavaScript.class);
        siddhiManager.setExtension("script:scala", EvalScala.class);

        String concatFunc1 =
                "define function concat Scala return string {\n" +
                        "  var concatenatedString = \"\"\n" +
                        "  for(i <- 0 until data.length) {\n" +
                        "     concatenatedString += data(i).toString\n" +
                        "  }\n" +
                        "  concatenatedString\n" +
                        "};";
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(concatFunc1);
        siddhiAppRuntime.shutdown();
    }

    @Test(expectedExceptions = SiddhiParserException.class)
    public void testWrongBrackets() throws InterruptedException {
        log.info("testDefineManyFunctionsAndCallThemRandom");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("script:javascript", EvalJavaScript.class);
        siddhiManager.setExtension("script:scala", EvalScala.class);

        String concatFunc1 =
                "define function concat(Scala) return string {\n" +
                        "  var concatenatedString = \"\"\n" +
                        "  for(i <- 0 until data.length) {\n" +
                        "     concatenatedString += data(i).toString\n" +
                        "  }\n" +
                        "  concatenatedString\n" +
                        "};";
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(concatFunc1);
        siddhiAppRuntime.shutdown();
    }

    @Test(expectedExceptions = SiddhiParserException.class)
    public void testMissingReturnTypeWhileParsing() throws InterruptedException {
        log.info("testDefineManyFunctionsAndCallThemRandom");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("script:javascript", EvalJavaScript.class);
        siddhiManager.setExtension("script:scala", EvalScala.class);

        String concatFunc1 =
                "define function concat[Scala] {\n" +
                        "  var concatenatedString = \"\"\n" +
                        "  for(i <- 0 until data.length) {\n" +
                        "     concatenatedString += data(i).toString\n" +
                        "  }\n" +
                        "  concatenatedString\n" +
                        "};";
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(concatFunc1);
        siddhiAppRuntime.shutdown();
    }
}
