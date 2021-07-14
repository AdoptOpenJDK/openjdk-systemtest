/*******************************************************************************
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/

package net.adoptopenjdk.stf; 

import static net.adoptopenjdk.stf.extensions.core.StfCoreExtension.Echo.ECHO_OFF;
import static net.adoptopenjdk.stf.extensions.core.StfCoreExtension.Echo.ECHO_ON; 

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import net.adoptopenjdk.stf.environment.DirectoryRef; 
import net.adoptopenjdk.stf.environment.FileRef;
import net.adoptopenjdk.stf.environment.JavaVersion;
import net.adoptopenjdk.stf.environment.PlatformFinder;
import net.adoptopenjdk.stf.environment.StfTestArguments; 
import net.adoptopenjdk.stf.extensions.core.StfCoreExtension;
import net.adoptopenjdk.stf.plugin.interfaces.StfPluginInterface;
import net.adoptopenjdk.stf.processes.ExpectedOutcome; 
import net.adoptopenjdk.stf.processes.StfProcess;
import net.adoptopenjdk.stf.processes.definitions.JavaProcessDefinition;
import net.adoptopenjdk.stf.processes.definitions.JavaProcessDefinition.JarId;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;

public class Jck implements StfPluginInterface {
	private static final Logger logger = LogManager.getLogger(JavaVersion.class.getName());
	
	// Variables which hold the input values
	private String testJdk;
	private String tests;
	private String testExecutionType;
	private String withAgent;
	private String interactive;
	private String extraJvmOptions;
	private String concurrencyString;
	private String jckVersion;
	private String config;
	private String jckRoot;
	
	private enum TestSuite{
		RUNTIME, COMPILER, DEVTOOLS; 
	}
	private TestSuite testSuite;
	
	// Variables which hold the derived values relative to the JCK root
	private DirectoryRef jckBase;
	private FileRef jtiFile;
	private DirectoryRef nativesLoc;
	private DirectoryRef jckConfigLoc;
	private String initialJtxFullPath;
	private String jtxRelativePath;
	private String jtxFullPath;
	private String kflRelativePath;
	private String kflFullPath;
	private FileRef krbConfFile;
	private String fileUrl;
	private String jckTopDir;
	
	// Variables which hold the values read from the properties file
	private String testProps;
	private String testHost1Name;
	private String testHost1Ip;
	private String testHost2Name;
	private String testHost2Ip;
	private String httpUrl;
	private String krb5ClientPassword;
	private String krb5ClientUsername;
	private String krb5ServerPassword;
	private String krb5ServerUsername;
	
	// Variables which hold the JCK test suite name and version details
	private String jckVersionNo; 						// Will contain only the version number. (ie) 6b, 7 , 8b etc
	private String testSuiteFolder;
	private String platform;	
	private String fileContent;
	
	// Variables to contain the JVM options required to suppress dumps being taken for OutOfMemory exceptions
	private String suppressOutOfMemoryDumpOptions = "";
	
	// Variables which hold the values needed for tests
	private int freePort;
	
	private static String archName = System.getProperty("os.arch");
	private static boolean isRiscv = archName.toLowerCase().contains("riscv");
	
	public void help(HelpTextGenerator help) {
		help.outputSection("JckTest");
		help.outputText("JckTest executes the Java Compatibility Kit (JCK).");
		
		help.outputSection("Set up");
		help.outputText("1. The JCK must be located one of the -systemtest-prereqs directories.\n"
				+ "2. A jcktest.properties and krb5.conf file must be created for the local configuration and added to a -systemtest-prereqs directory.\n"
				+ "See test.jck/docs/README.md for detailed setup instructions.\n");
		
		help.outputSection("Options");
		
		help.outputArgName("tests", "NAME");
		help.outputArgDesc("This is the name of test package to run, eg, 'api/java_awt', 'vm/jni', etc.");
		
		help.outputArgName("jckversion", "NAME");
		help.outputArgDesc("This is the jck testsuite version to use, eg, 'jck6b', 'jck7' etc.");
		
		help.outputArgName("testsuite", "[runtime|compiler|devtools]");
		help.outputArgDesc("This is the type of the testsuite which we are going to run.");
		
		help.outputArgName("config", "NAME");
		help.outputArgDesc("This is the location of the directory containing the jcktest.properties and krb5.conf files.  Defaults to 'default'.");
		
		help.outputArgName("executiontype", "[multijvm|multijvmgroup|singlejvm]");
		help.outputArgDesc("This option is an input to the JCK harness to specify how to execute the JCK tests.");
		
		help.outputArgName("withagent", "[on|off]");
		help.outputArgDesc("This option is an input to the JCK harness to specify whether to run with the agent option.");
		
		help.outputArgName("interactive", "[yes|no]");
		help.outputArgDesc("This option is an input to the JCK harness to specify whether to run the interative tests.\n"
				+ "Note this should option should only be set to 'yes' if you have access to a display, keyboard and mouse "
				+ "attached to the test machine and have allocated sufficient time (several hours) to complete the testing,");
		
		help.outputArgName("concurrency", "cpus|nn");
		help.outputArgDesc("This option is an input to the JCK harness to specify whether to run tests in parallel.\n"
				+ "concurrency=nn means run nn tests in parallel.\n"
				+ "concurrency=cpus means run Runtime.getRuntime().availableProcessors() plus one tests in parallel.\n"
				+ "If the concurrency option is not set, concurrency=1 is used.");
	}

	public void pluginInit(StfCoreExtension test) throws Exception {
		StfTestArguments testArgs = test.env().getTestProperties("tests","jckversion","testsuite","config=[NULL]","executiontype=[multijvm]","withagent=[off]","interactive=[no]","jckRoot=[NULL]","concurrency=[NULL]");
		
		testJdk = System.getenv("JAVA_HOME");
		tests = testArgs.get("tests");	
		jckVersion = testArgs.get("jckversion");	
		jckVersionNo = jckVersion.replace("jck", "");		
		
		// If you want to run JCK tests using a specific Test suite. eg:  jckRoot=/jck/java9/jck-9-ea-tck-b46 or jckRoot=C:\jck\
		// To use this option the path must be fully qualified.
		jckRoot = testArgs.get("jckRoot");
		
		if (jckRoot.equals("NULL")) {
			jckTopDir = determineJckRoot(test, jckVersion);
			jckRoot = test.env().findPrereqDirectory(jckTopDir + jckVersion + "/").getPath();
			logger.info("jckRoot determined to be " + jckRoot);
		}
		else {
			logger.info("Using -test-args supplied jckRoot " + jckRoot);
			jckTopDir = jckRoot; 
		}
		
		File f = new File(jckRoot);
		File[] files = f.listFiles();
		boolean found = false;
		for (File file : files) {
			logger.info("Found " + file.getName());
			if (file.isDirectory() && (file.getName().contains("JCK-runtime")) ) {
				found = true;
				String actualJckVersion = "jck" + file.getName().split("-")[2];
				logger.info("jckversion determined to be " + actualJckVersion);
				if (!jckVersion.equals("NULL")) {
					if (!jckVersion.equals(actualJckVersion)) {
						logger.info("test-args jckversion " + jckVersion + " does not match actual jckversion " + actualJckVersion + ". Using actual jckversion " + actualJckVersion);
						jckVersion = actualJckVersion;
					}
				}
			}
		}
		if ( !found ) {
			throw new StfException(testExecutionType + "Cannot locate the JCK. It must either be placed in a jck subdirectory within a -systemtest-prereqs directory (e.g. /home/user/systemtest_prereqs/jck/jck8b) or be provided via the -test-args=\"jckRoot=xxxx\" option. The check failed because " + jckRoot + " does not point to a directory containing a JCK-runtime directory");
		}
		testSuite = testArgs.decodeEnum("testsuite", TestSuite.class);
		testExecutionType = testArgs.get("executiontype");
		withAgent = testArgs.get("withagent");
		interactive = testArgs.get("interactive");
		concurrencyString = testArgs.get("concurrency");
		testSuiteFolder = "JCK-" + testSuite.toString().toLowerCase() + "-" + jckVersionNo;
		platform = test.env().getOsgiOperatingSystemName();	
		jckBase = test.env().findPrereqDirectory(testSuiteFolder);
		nativesLoc = test.env().findPrereqDirectory("natives/" + test.env().getPlatformSimple());

		DirectoryRef repositoryConfigLoc = test.env().findTestDirectory("openjdk.test.jck/config");
		jtiFile = repositoryConfigLoc.childFile("/" + jckVersion + "/" + testSuite.toString().toLowerCase() + ".jti");
		//fileUrl = "file:///" + jckRoot + "/" + testSuiteFolder + "/testsuite.jtt";
		
		fileUrl = "file:///" + test.env().findPrereqFile(testSuiteFolder + "/testsuite.jtt");
		
		// The first release of a JCK will have an initial excludes (.jtx) file in test-suite/lib - e.g. JCK-runtime-8b/lib/jck8b.jtx.
		// Updates to the excludes list may subsequently be supplied as a separate file, which supersedes the initial file.
		// A known failures list (.kfl) file is optional.
		// The automation here adds any files found (initial or updates) as 'custom' files. 
		initialJtxFullPath = jckBase + "/lib/" + jckVersion + ".jtx";

		// Look for an update to the initial excludes file
		if (jckVersion.contains("jck6") || jckVersion.contains("jck7")) {
			jtxRelativePath = "excludes/jdk" + jckVersionNo + ".jtx";
			kflRelativePath = "excludes/jdk" + jckVersionNo + ".kfl";
		} else {
			jtxRelativePath = "excludes/jck" + jckVersionNo + ".jtx";
			kflRelativePath = "excludes/jck" + jckVersionNo + ".kfl";
		}
		try {
			jtxFullPath = test.env().findPrereqFile(jtxRelativePath).toString();
			logger.info("Using excludes list file " + jtxFullPath);
		} catch (StfException e) {
			logger.info("Unable to find excludes list file " + jtxRelativePath);
			jtxFullPath = "";
		}

		// Look for a known failures list file
		try {
			kflFullPath = test.env().findPrereqFile(kflRelativePath).toString();
			logger.info("Using known failures list file " + kflFullPath);
		} catch (StfException e) {
			logger.info("Unable to find known failures list file " + kflRelativePath);
			kflFullPath = "";
		}
		
		// Some of the tests require details of remote services (http and kerberos).
		// To make it easy for people to run as much of the JCK as possible with no additional setup
		// we only insist on the presence of those files if those tests are requested to be executed.
		// findPrereqDirectory will throw an exception if the directory cannot be found, so we need to catch it.
		config = testArgs.get("config");
		if ( ( testSuite == TestSuite.RUNTIME ) && ( tests.contains("api/java_net") || tests.contains("api/java_nio") || tests.contains("api/org_ietf") || tests.contains("api/javax_security") || tests.equals("api") ) ) {
			if (config.equals("NULL")) {
				config = "default";	
			}
			krbConfFile = repositoryConfigLoc.childFile("/" + config + "/krb5.conf");
			testProps = repositoryConfigLoc.childFile("/" + config + "/jcktest.properties").toString();
			Properties prop = new Properties();
			InputStream ins = new FileInputStream(testProps);
			prop.load(ins);
			testHost1Name = prop.getProperty("testhost1name");
			testHost1Ip = prop.getProperty("testhost1ip");
			testHost2Name = prop.getProperty("testhost2name");
			testHost2Ip = prop.getProperty("testhost2ip");
			httpUrl = prop.getProperty("httpurl");
			// Make sure username properties do not have trailing whitespace before adding server location data.
			krb5ClientPassword = prop.getProperty("krb5ClientPassword");
			krb5ClientUsername = prop.getProperty("krb5ClientUsername");
			if ( krb5ClientUsername != null ) {
				krb5ClientUsername = krb5ClientUsername.trim();
			}
			krb5ServerPassword = prop.getProperty("krb5ServerPassword");
			krb5ServerUsername = prop.getProperty("krb5ServerUsername");
			if ( krb5ServerUsername != null ) {
				krb5ServerUsername = krb5ServerUsername.trim();
			}
		}
		
		// Some tests provoke out of memory exceptions.
		// If we're testing a J9 VM that will result in dumps being taken and a non-zero return code
		// which stf will detect as a failure. So in this case add the -Xdump options required to suppress
		// taking dumps for OutOfMemory.
		if (test.env().primaryJvm().isIBMJvm()) {
			suppressOutOfMemoryDumpOptions = " -Xdump:system:none -Xdump:system:events=gpf+abort+traceassert+corruptcache -Xdump:snap:none -Xdump:snap:events=gpf+abort+traceassert+corruptcache -Xdump:java:none -Xdump:java:events=gpf+abort+traceassert+corruptcache -Xdump:heap:none -Xdump:heap:events=gpf+abort+traceassert+corruptcache";
		}
	}
	
	
	public void setUp(StfCoreExtension test) throws Exception {
		DirectoryRef newJtbLocation = test.env().getResultsDir().childDirectory("JTB");
		test.doMkdir("Creating dir to store the custom jtb", newJtbLocation);
		FileRef newJtbFileRef = newJtbLocation.childFile("stf_generated.jtb");
		
		String workDir = test.env().getResultsDir().childDirectory("workdir").toString();
		String reportDir = test.env().getResultsDir().childDirectory("report").toString();
		
		fileContent = "testsuite \"" + jckBase + "\";\n";
		fileContent += "workDirectory -create -overwrite " + workDir + ";\n";
		fileContent += "tests " + tests + ";\n";
			
		if (testExecutionType.equals("multijvm") && withAgent.equals("off")) {
			jckConfigurationForMultijvmWithNoAgent(test);
		} else {
			throw new StfException(testExecutionType + "with Agent " + withAgent + "combination is not yet supported.");
		}
		
		fileContent += "set jck.excludeList.customFiles \"" + initialJtxFullPath + " " + jtxFullPath + " " + kflFullPath + "\"" + ";\n";
		fileContent += "runTests" + ";\n";
		fileContent += "writeReport -type xml " + reportDir + ";\n";
		
		// Make sure any backslashes are escaped, required by the test harness.
		fileContent = fileContent.replace("\\\\", "\\"); 		// Replaces \\ with \, leave \ alone.
		fileContent = fileContent.replace("\\", "\\\\");		// Replaces \ with \\
		 
		test.doWriteFile("Writing into jtb file.", newJtbFileRef, fileContent);
		
		test.doEchoFile("The JTB file generated\n", newJtbFileRef);

		if ( PlatformFinder.isZOS() ) {
			test.doIconvFile("Converting .jtb file to ascii", newJtbFileRef.getSpec(), "IBM-1047", "ISO8859-1");
		}

	}

	public void execute(StfCoreExtension test) throws Exception {
		String comment = "Running JCK in " + testExecutionType + " way with Agent " + withAgent;
		FileRef jtbFile = test.env().getResultsDir().childDirectory("JTB").childFile("stf_generated.jtb");
		if (testExecutionType.equals("multijvm") && withAgent.equals("off")) {
			StfProcess javatestAgent = null;
			StfProcess rmiRegistry = null;
			StfProcess rmid = null;
			StfProcess tnameserv = null;
			String executeJar = "/lib/javatest.jar";
			
			if ( (testSuite == TestSuite.RUNTIME) && (tests.contains("api/java_util") || tests.contains("api/java_net") || tests.contains("api/java_rmi")  || tests.contains("api/javax_management") 
					|| tests.contains("api/org_omg") || tests.contains("api/javax_xml") || tests.equals("api") || tests.contains("vm/jdwp") || tests.equals("vm")) ) {
				String addModules = "";
				// JCK 9/10 javatest agents need to be given access to non default modules.
				// JCK 8 doesn't support modules, JCK11 and beyond have removed these two modules: java.xml.ws,java.corba.
				if ( jckVersion.contains("jck9") || jckVersion.contains("jck10") ) {
					addModules = "--add-modules java.xml.ws,java.corba";
				}
				javatestAgent = test.doRunBackgroundProcess("Starting javatest agent", "AGNT", ECHO_ON, ExpectedOutcome.neverCompletes(), test.createJavaProcessDefinition()
						.addJvmOption("-Djavatest.security.allowPropertiesAccess=true -Djava.security.policy=" + test.env().findPrereqFile(testSuiteFolder + "/lib/jck.policy").toString())
						.addJvmOption(addModules)
						.addJarToClasspath(test.env().findPrereqFile(testSuiteFolder + executeJar))
						.addDirectoryToClasspath(test.env().findPrereqDirectory(testSuiteFolder + "/classes"))
						.runClass("com.sun.javatest.agent.AgentMain")
						.addArg(" -passive")
						);
				
				// We only need RMI registry and RMI activation daemon processes for tests under api/java_rmi
				if (tests.contains("api/java_rmi")) { 
					rmiRegistry = test.doRunBackgroundProcess("Starting RMI registry", "RMI", ECHO_ON, ExpectedOutcome.neverCompletes(), test.createJDKToolProcessDefinition()
							.setJDKToolOrUtility("rmiregistry")	);
					
					rmid = test.doRunBackgroundProcess("Starting RMI activation system daemon", "RMID", ECHO_ON, ExpectedOutcome.neverCompletes(), test.createJDKToolProcessDefinition()
							.setJDKToolOrUtility("rmid")	
							.addArg("-J-Dsun.rmi.activation.execPolicy=none " + "-J-Djava.security.policy=" + test.env().findPrereqFile(testSuiteFolder + "/lib/jck.policy").toString())
							);
				}
				
				// tnameserv has been removed from jdk11. We only should need it for jck8
				if (jckVersion.contains("jck8")) { 
					tnameserv = test.doRunBackgroundProcess("Starting tnameserver", "TNAM", ECHO_ON, ExpectedOutcome.neverCompletes(), test.createJDKToolProcessDefinition()
						.setJDKToolOrUtility("tnameserv")	
						.addArg("-ORBInitialPort 9876")
						);
				}
			}
			
			String commandLine = " -config "+ jtiFile + " @" + jtbFile;

			ExpectedOutcome outcome;
			String timeout = "24h";
			// Use the presence of a '/' to signify that we are running a subset of tests.
			// If one of the highest level test nodes is being run it is likely to take a long time.
			if ( tests.contains("/") && !isRiscv ) {
				timeout = "8h";
			}
			outcome = ExpectedOutcome.cleanRun().within(timeout);

			test.doRunForegroundProcess(comment, "JCK", ECHO_ON, outcome, test.createJavaProcessDefinition().setExecutableJar(test.env().findPrereqFile(testSuiteFolder + executeJar)).addArg(commandLine));
			// The Compiler -ANNOT, EXPR and LMBD may take over 6 hours to run on some machines.
			if (javatestAgent != null) {
				test.doKillProcesses("Stopping javatest agent", javatestAgent);
			}
			if (rmiRegistry != null) {
				test.doKillProcesses("Stopping rmiregistry", rmiRegistry);
			}
			if (rmid != null) {
				test.doKillProcesses("Stopping rmid", rmid);
			}
			if (tnameserv != null) {
				test.doKillProcesses("Stopping tnameserver", tnameserv);
			}
		}
	}

	public void tearDown(StfCoreExtension test) throws Exception {
		String timeout = isRiscv ? "24h" : "2m";
		test.doRunForegroundProcess("Generating Result Summary", "RS", ECHO_ON, 
			ExpectedOutcome.cleanRun().within(timeout), test.createJavaProcessDefinition()
			.addProjectToClasspath("openjdk.test.jck")
			.runClass(SummaryGenerator.class)
			.addArg(test.env().getResultsDir().childDirectory("report/xml").childFile("report.xml").getSpec()));
		
	}
	
	private static class KerberosConfig {
		static String kdcRealmName;
		static String kdcHostName;
		KerberosConfig(String kdcRealmName,String kdcHostName) {
		KerberosConfig.kdcRealmName = kdcRealmName;
		KerberosConfig.kdcHostName = kdcHostName;
		}
	}

	private KerberosConfig readKrbConfFile() throws Exception {
		String kdcRealmName = null;
		String kdcHostName = null;
		BufferedReader br = new BufferedReader(new FileReader(krbConfFile.toString()));
		String thisLine = null;
	
		while ((thisLine = br.readLine()) != null ) {
			if (thisLine.contains("default_realm")) {
				String[] parts = thisLine.split("=");
				kdcRealmName = parts[1].trim();
			}
			if (thisLine.contains("admin_server")) {
				String[] parts = thisLine.split("=");
				kdcHostName = parts[1].trim();
			}
		 }
		 br.close();
		 return new KerberosConfig(kdcRealmName, kdcHostName);
	}

	private int getFreePort() throws Exception {
		int freePort = -1;
		
		ServerSocket s = new ServerSocket(0);
		freePort = s.getLocalPort();
		s.close();
		
		return freePort;
	}
	
	private String locateFileOrFolder(String root, String pattern) throws Exception {
		String matches = "";
		
		StringBuffer sb = new StringBuffer();
		File f = new File(root);
		File[] files = f.listFiles();
		for (File file : files) {
			if (file.getName().contains(pattern)) {
				sb.append(file.toString()).append(File.pathSeparator);
			}
			
			if (file.isDirectory() && !(file.getName().contains("ext")) ) {
				String s = locateFileOrFolder(file.toString(), pattern);
				if (!s.equals("")) {
					sb.append(s);
				}
			}		
		}
		matches = sb.toString();
		return matches;
	}
	
	private String getSignatureTestJars(String root) throws Exception {
		String jarsList = "";
		StringBuffer sb = new StringBuffer();
		logger.info("Looking for .jar files in " + root + " for signaturetest.");
		
		sb.append(locateFileOrFolder(root,".jar"));
		
		String newRoot = new File(root).getParent() + File.separator + "bin";
		logger.info("Looking for vm.jar in " + newRoot + " for signaturetest.");
		sb.append(locateFileOrFolder(newRoot,"vm.jar"));
		
		jarsList = sb.toString();
		logger.info("Using " + jarsList + " for signaturetest.");
		return jarsList;
	}
	
	public void jckConfigurationForMultijvmWithNoAgent(StfCoreExtension test) throws Exception {
		String pathToJava = test.createJavaProcessDefinition().getCommand();
		String pathToRmic = testJdk + File.separator + "bin" + File.separator + "rmic";
		String pathToLib = testJdk + File.separator + "jre" + File.separator + "lib";
		String pathToJavac = testJdk + File.separator + "bin" + File.separator + "javac";
		String pathToToolsJar = testJdk + File.separator + "lib" + File.separator + "tools.jar";
		// Use escaped backslashes for paths on Windows
		if (platform.equals("win32")) {
			pathToJava = pathToJava.replace("/", "\\") + ".exe";
			pathToRmic = pathToRmic.replace("/", "\\") + ".exe";
			pathToLib = pathToLib.replace("/", "\\");
			pathToJavac = pathToJavac.replace("/", "\\") + ".exe";
			pathToToolsJar = pathToToolsJar.replace("/", "\\");
		}
		
		DirectoryRef jckRuntimeNativeLibValue = nativesLoc;
		DirectoryRef jckRuntimeJmxLibValue = nativesLoc;
		int concurrency;
		String keyword = "";
		String libPath = "";
		String robotAvailable = "";
		String hostname = "";
		String ipAddress = "";
		extraJvmOptions = "";
		
		extraJvmOptions += " " + test.getJavaArgs(test.env().primaryJvm()) + " ";

		InetAddress addr = InetAddress.getLocalHost();
		ipAddress = addr.getHostAddress();
		hostname = addr.getHostName();

		freePort = getFreePort();
		
		if (freePort == -1) {
			throw new StfException("Unable to get a free port");
		}

		// If concurrency was not specified as a test-arg it will have been assigned the value NULL.
		// Default to concurrency=1.
		if ( concurrencyString.equals("NULL") ) {
			concurrencyString = "1";
		}

		// If concurrency=cpus was specified, set concurrency to the number of processors + 1.
		if ( concurrencyString.equals("cpus") ) {
			concurrency = Runtime.getRuntime().availableProcessors() + 1;
			concurrencyString = String.valueOf(concurrency);
		}
		
		// Set the operating system as 'Windows' for Windows and 'other' for all other operating systems.
		// If 'other' is specified when executing on Windows, then Windows specific settings such
		// as systemRoot are rejected, and the JCK harness assumes that DISPLAY is required for GUI tests.
		// Otherwise 'other' is required because the JCK harness has no inherent knowledge of AIX and zOS.
		// Runtime settings
		if (testSuite == TestSuite.RUNTIME) {
			if ( interactive.equals("yes")) {
				keyword = "keywords interactive";
			}
			else { 
				keyword = "keywords !interactive";
			}
			
			if (platform.equals("win32")) {
				libPath = "PATH";
				robotAvailable = "Yes";
			} else if (platform.equals("linux"))  {
				libPath = "LD_LIBRARY_PATH";
				robotAvailable = "Yes";
			} else if (platform.equals("solaris"))  {
				libPath = "LD_LIBRARY_PATH";
				robotAvailable = "Yes";
			} else if (platform.equals("aix")) {
				libPath = "LIBPATH";
				robotAvailable = "Yes";
			} else if (platform.equals("zos")) {
				pathToLib = testJdk + File.separator + "lib";
				libPath = "LIBPATH";
				robotAvailable = "No";
			} else if (platform.equals("macosx")) {
				libPath = "DYLD_LIBRARY_PATH";
				robotAvailable = "Yes";
			} else {
				throw new StfException("Unknown platform:: " + platform);
			}
			fileContent += "concurrency " + concurrencyString + ";\n";
			fileContent += "timeoutfactor 2" + ";\n";	// 2 base time limit equal 20 minutes
			fileContent += keyword + ";\n";
			
			if (platform.equals("win32")) {
				// On Windows set the testplatform.os to Windows and set systemRoot, but do not
				// set the file and path separators (an error is thrown if they are set).
				fileContent += "set jck.env.testPlatform.os \"Windows\";\n";
				fileContent += "set jck.env.testPlatform.systemRoot " + System.getenv("WINDIR") + ";\n";
			}
			else {
				// On other platforms set the testplatform.os to other and set the file and path separators.
				fileContent += "set jck.env.testPlatform.os \"other\";\n";
				fileContent += "set jck.env.testPlatform.fileSep \"/\";\n";
				fileContent += "set jck.env.testPlatform.pathSep \":\";\n";
			}
			
			if ( testsRequireDisplay(tests) ) {
				if (platform.equals("zos")) {
					fileContent += "set jck.env.testPlatform.headless Yes" + ";\n";
					fileContent += "set jck.env.runtime.testExecute.otherEnvVars LIBPATH=/usr/lpp/tcpip/X11R66/lib" + ";\n";
				}
				else {
					if ( !platform.equals("win32") ) {
						fileContent += "set jck.env.testPlatform.headless No" + ";\n";
						fileContent += "set jck.env.testPlatform.xWindows Yes" + ";\n";
						String display = System.getenv("DISPLAY");
						if ( display == null ) {
							throw new StfException("Error: DISPLAY must be set to run tests " + tests + " on " + platform);
						}
						else {
							fileContent += "set jck.env.testPlatform.display " + display + ";\n";
						}
					}
				}
			}

			if ( tests.contains("api/java_awt") || tests.contains("api/javax_swing") || tests.equals("api") ) {
				keyword += "&!robot";
			}

			if ( !platform.equals("win32") &&
                             (tests.contains("api/signaturetest") || tests.contains("api/java_io")) ) {
				fileContent += "set jck.env.testPlatform.xWindows \"No\"" + ";\n";
			}

			fileContent += "set jck.env.runtime.testExecute.cmdAsString \"" + pathToJava + "\"" + ";\n";
			
			if ( tests.equals("api/java_lang") || tests.contains("api/java_lang/instrument") ||
				tests.contains("api/javax_management") || tests.equals("api") || tests.startsWith("vm") ) {
				fileContent += "set jck.env.runtime.testExecute.libPathEnv " + libPath + ";\n";
				fileContent += "set jck.env.runtime.testExecute.nativeLibPathValue \"" + jckRuntimeNativeLibValue + "\"" + ";\n";
			}
			// tools.jar was incorporated into modules from Java 9
			if ( jckVersion.contains("jck8") ) {
				if ( tests.startsWith("vm/jvmti") || tests.equals("vm") || tests.equals("api") || tests.equals("api/java_lang") || tests.contains("api/java_lang/instrument") ) {
					fileContent += "set jck.env.runtime.testExecute.additionalClasspathRemote \"" + pathToToolsJar + "\"" + ";\n";
				}
			}

			if ( tests.startsWith("vm/jvmti") || tests.equals("vm") ) {
				fileContent += "set jck.env.runtime.testExecute.jvmtiLivePhase Yes;\n";
			}

			if ( tests.contains("api/javax_management") || tests.equals("api") ) {
				fileContent += "set jck.env.runtime.testExecute.jmxResourcePathValue \"" + jckRuntimeJmxLibValue + "\"" + ";\n";
			}
			if ( tests.contains("api/javax_sound") || tests.equals("api") ) {
				fileContent += "set jck.env.runtime.audio.canPlaySound No" + ";\n";
				fileContent += "set jck.env.runtime.audio.canPlayMidi No" + ";\n";
				fileContent += "set jck.env.runtime.audio.canRecordSound No" + ";\n";
			}
			if ( tests.contains("api/org_ietf") || tests.contains("api/javax_security") || tests.equals("api") ) {
				readKrbConfFile();
				if (KerberosConfig.kdcHostName == null || KerberosConfig.kdcRealmName == null){
					throw new StfException(tests + "expects kdcHostname and kdcRealmname. Recheck if the values are proper in the supplied kdc conf file.");
				}
				
				fileContent += "set jck.env.runtime.jgss.krb5ClientPassword " + krb5ClientPassword + ";\n";
				fileContent += "set jck.env.runtime.jgss.krb5ClientUsername " +  krb5ClientUsername + "/" + KerberosConfig.kdcHostName+'@'+KerberosConfig.kdcRealmName + ";\n";
				fileContent += "set jck.env.runtime.jgss.krb5ServerPassword " + krb5ServerPassword + ";\n";
				fileContent += "set jck.env.runtime.jgss.krb5ServerUsername " +  krb5ServerUsername + "/" + KerberosConfig.kdcHostName+'@'+KerberosConfig.kdcRealmName + ";\n";
				fileContent += "set jck.env.runtime.jgss.kdcHostName " + KerberosConfig.kdcHostName + ";\n";
				fileContent += "set jck.env.runtime.jgss.kdcRealmName " + KerberosConfig.kdcRealmName + ";\n";
				
				extraJvmOptions += "-Djava.security.krb5.conf=" + krbConfFile + " -DKRB5CCNAME=" + test.env().getResultsDir().toString() + File.separator + "krb5.cache" + " -DKRB5_KTNAME=" + test.env().getResultsDir().toString() + File.separator + "krb5.keytab";
			}	
			if ( tests.contains("api/java_net") || tests.contains("api/java_nio") || tests.equals("api") ) {
				fileContent += "set jck.env.runtime.net.localHostName " + hostname + ";\n";
				fileContent += "set jck.env.runtime.net.localHostIPAddr " + ipAddress + ";\n";
				fileContent += "set jck.env.runtime.net.testHost1Name " + testHost1Name + ";\n";
				fileContent += "set jck.env.runtime.net.testHost1IPAddr " + testHost1Ip + ";\n";
				fileContent += "set jck.env.runtime.net.testHost2Name " + testHost2Name + ";\n";
				fileContent += "set jck.env.runtime.net.testHost2IPAddr " + testHost2Ip + ";\n";
			}
			if ( tests.contains("api/java_net") || tests.equals("api") ) {
				fileContent += "set jck.env.runtime.url.httpURL " + httpUrl + ";\n";
				fileContent += "set jck.env.runtime.url.fileURL " + fileUrl + ";\n";
			}
			if ( tests.contains("api/java_net") || tests.contains("api/org_omg") || tests.contains("api/javax_management") || tests.contains("api/javax_xml") || tests.contains("vm/jdwp") || tests.equals("api")) {
				if ( !tests.contains("api/javax_xml/bind") &&
					 !tests.contains("api/javax_xml/soap") &&
					 !tests.contains("api/org_omg/PortableInterceptor") &&
					 !tests.contains("api/org_omg/PortableServer") ) {
					fileContent += "set jck.env.runtime.remoteAgent.passiveHost localhost" + ";\n";
					fileContent += "set jck.env.runtime.remoteAgent.passivePortDefault Yes" + ";\n";
				}
			}
			// Without the following override the following failures occur:
			// Fatal Error: file:/jck/jck8b/JCK-runtime-8b/tests/api/javax_xml/xmlCore/w3c/ibm/valid/P85/ibm85v01.xml(6,3384): JAXP00010005: The length of entity "[xml]" is "3,381" that exceeds the "1,000" limit set by "FEATURE_SECURE_PROCESSING".
			// Fatal Error: file:/jck/jck8b/JCK-runtime-8b/tests/api/javax_xml/xmlCore/w3c/ibm/valid/P85/ibm85v01.xml(6,3384): JAXP00010005: The length of entity "[xml]" is "3,381" that exceeds the "1,000" limit set by "default".
			if ( tests.contains("api/javax_xml")  || tests.equals("api")) {
				extraJvmOptions += " -Djdk.xml.maxXMLNameLimit=4000";
			}	
			
			//CORBA related files (e.g. tnameserver) were removed post Java 9
			if (jckVersion.contains("jck8")) {
				if ( tests.contains("api/org_omg") || tests.contains("api/javax_management") || tests.equals("api") ) {
					fileContent += "set jck.env.runtime.idl.orbHost " + hostname + ";\n";
				}
			}
			// ext/lib was removed at Java 9
			if ( jckVersion.contains("jck8") ) {
				if ( tests.contains("api/java_text") || tests.contains("api/java_util") || tests.equals("api")) {
					extraJvmOptions += " -Djava.ext.dirs=" + jckBase + File.separator + "lib" + File.separator + "extensions" + File.pathSeparator + 
						testJdk + File.separator + "jre" + File.separator + "lib" + File.separator + "ext";
				}
			}
			if (jckVersion.contains("jck8")) {
				if (tests.contains("api/signaturetest") || tests.equals("api")) {
					fileContent += "set jck.env.runtime.staticsigtest.staticSigTestClasspathRemote \"" + getSignatureTestJars(pathToLib) + "\"" + ";\n";
				}
			}
			if (extraJvmOptions.contains("nofallback") && tests.startsWith("vm") ) {
				fileContent += "set jck.env.testPlatform.typecheckerSpecific No" + ";\n";		
			}
			
			// The jplisLivePhase and Robot available settings are rejected if placed higher up in the .jtb file
			if ( tests.contains("api/java_awt") || tests.contains("api/javax_swing") || tests.equals("api") ) {
				fileContent += "set jck.env.runtime.awt.robotAvailable " + robotAvailable + ";\n";
			}
			if ( tests.equals("api/java_lang") || tests.contains("api/java_lang/instrument") || tests.equals("api") ) {
				fileContent += "set jck.env.runtime.jplis.jplisLivePhase Yes;\n";
			}
			
			// Get any additional jvm options for specific tests.
			extraJvmOptions += getTestSpecificJvmOptions(jckVersion, tests);

			extraJvmOptions += suppressOutOfMemoryDumpOptions;
			
			if (jckVersion.contains("jck12") || jckVersion.contains("jck13")) {
				extraJvmOptions += " --enable-preview";
			}
			
			// Add the JVM options supplied by the user plus those added in this method to the jtb file option.
			fileContent += "set jck.env.runtime.testExecute.otherOpts \" " + extraJvmOptions + " \"" + ";\n";
		}
		
		// Compiler settings
		if (testSuite == TestSuite.COMPILER) {
			
			keyword = "keywords compiler";
			
			// Overrides only required on zOS for compiler tests
			if (platform.equals("win32")) {
			} else if (platform.equals("linux")) {
			} else if (platform.equals("macosx")) {
			} else if (platform.equals("solaris")) {
			} else if (platform.equals("aix")) {
			} else if (platform.equals("zos")) {
				pathToLib = testJdk + File.separator + "lib";
			} else {
				throw new StfException("Unknown platform:: " + platform);
			}
			
			fileContent += "concurrency " + concurrencyString + ";\n";
			fileContent += "timeoutfactor 4" + ";\n";							// lang.CLSS,CONV,STMT,INFR requires more than 1h to complete. lang.Annot,EXPR,LMBD require more than 2h to complete tests
			fileContent += keyword + ";\n";

			String cmdAsStringOrFile = "cmdAsString"; // Whether to reference cmd via cmdAsString or cmdAsFile
			if (platform.equals("win32")) {
				// On Windows set the testplatform.os to Windows and set systemRoot, but do not
				// set the file and path separators (an error is thrown if they are set).
				fileContent += "set jck.env.testPlatform.os \"Windows\";\n";
				fileContent += "set jck.env.testPlatform.systemRoot " + System.getenv("WINDIR") + ";\n";
			} else if (platform.equals("zos") || platform.equals("aix")) {
				// On z/OS and AIX set the testplatform.os Current system
				// due to JCK class OsHelper bug with getFileSep() in Compiler JCK Interviewer
				fileContent += "set jck.env.testPlatform.os \"Current system\";\n";
				cmdAsStringOrFile = "cmdAsFile";
			} else {
				// On other platforms set the testplatform.os to other and set the file and path separators.
				fileContent += "set jck.env.testPlatform.os \"other\";\n";
				fileContent += "set jck.env.testPlatform.fileSep \"/\";\n";
				fileContent += "set jck.env.testPlatform.pathSep \":\";\n";
			}
			
			// If the Select Compiler question in the JCK interview was answered as "Java Compiler API (JSR199)",
			// set jck.env.compiler.testCompile.testCompileAPImultiJVM.cmdAsString.
			fileContent += "set jck.env.compiler.testCompile.testCompileAPImultiJVM." + cmdAsStringOrFile + " \"" + pathToJava + "\"" + ";\n";

			// If the Select Compiler question in the JCK interview was answered as "command line tool",
			// set set jck.env.compiler.testCompile.cmdAsString.
			// fileContent += "set jck.env.compiler.testCompile.cmdAsString \"" + pathToJavac + "\"" + ";\n";

			if (jckVersion.contains("jck8")) {
				fileContent += "set jck.env.compiler.testCompile.otherOpts \"-source 1.8 \"" + ";\n";
				if (tests.contains("api/signaturetest") || tests.equals("api")) {
					fileContent += "set jck.env.compiler.testCompile.compilerstaticsigtest.compilerStaticSigTestClasspathRemote \"" + getSignatureTestJars(pathToLib) + "\"" + ";\n";
				}
			} else if (jckVersion.contains("jck11")) {
				fileContent += "set jck.env.compiler.testCompile.otherOpts \"-source 11 \"" + ";\n";
			} else { // This is the case where JCK Version > 11
				fileContent += "set jck.env.compiler.testCompile.otherOpts \"-source " + jckVersionNo + " --enable-preview\"" + ";\n";
			} 
			
			if (tests.contains("api/java_rmi") || tests.equals("api")) {
				fileContent += "set jck.env.compiler.testRmic." + cmdAsStringOrFile + " \"" + pathToRmic + "\"" + ";\n";
			}
			fileContent += "set jck.env.compiler.compRefExecute." + cmdAsStringOrFile + " \"" + pathToJava + "\"" + ";\n";

			if (platform.equals("zos") || platform.equals("aix")) {
                                // On z/OS and AIX set the compRefExecute file and path separators
                                // due to JCK class OsHelper bug with getFileSep() in Compiler JCK Interviewer
                                fileContent += "set jck.env.compiler.compRefExecute.fileSep \"/\";\n";
                                fileContent += "set jck.env.compiler.compRefExecute.pathSep \":\";\n";
			}

			extraJvmOptions += suppressOutOfMemoryDumpOptions;
			
			if (jckVersion.contains("jck12") || jckVersion.contains("jck13")) {
				extraJvmOptions += " --enable-preview";
			}
			
			// Add the JVM options supplied by the user plus those added in this method to the jtb file option.
			fileContent += "set jck.env.compiler.compRefExecute.otherOpts \" " + extraJvmOptions + " \"" + ";\n";	
		}
		// Devtools settings
		if (testSuite == TestSuite.DEVTOOLS) {
		
			String xjcCmd = "";				// Required for all devtools test, except "java2schema" & "jaxws"
			String jxcCmd = "";				// Required for "java2schema" test
			String genCmd,impCmd  = "";		// Required for "jaxws" test
		
			if (platform.equals("win32")) {
				String winscriptdir;
				if ( jckVersion.contains("jck6") || jckVersion.contains("jck7") || jckVersion.contains("jck8") || jckVersion.contains("jck9") ) {
					winscriptdir="win32";
				} else {
					winscriptdir="windows";
				}
				xjcCmd = jckBase + File.separator + winscriptdir + File.separator + "bin" + File.separator + "xjc.bat"; 
				jxcCmd = jckBase + File.separator + winscriptdir + File.separator + "bin" + File.separator + "schemagen.bat"; 
				genCmd = jckBase + File.separator + winscriptdir + File.separator + "bin" + File.separator + "wsgen.bat"; 
				impCmd = jckBase + File.separator + winscriptdir + File.separator + "bin" + File.separator + "wsimport.bat"; 
			} else if (platform.equals("linux") || platform.equals("aix")) {
				xjcCmd = jckBase + File.separator + "linux" + File.separator + "bin" + File.separator + "xjc.sh";
				jxcCmd = jckBase + File.separator + "linux" + File.separator + "bin" + File.separator + "schemagen.sh";
				genCmd = jckBase + File.separator + "linux" + File.separator + "bin" + File.separator + "wsgen.sh";
				impCmd = jckBase + File.separator + "linux" + File.separator + "bin" + File.separator + "wsimport.sh";
			} else if (platform.equals("macosx")) {
				xjcCmd = jckBase + File.separator + "macos" + File.separator + "bin" + File.separator + "xjc.sh";
				jxcCmd = jckBase + File.separator + "macos" + File.separator + "bin" + File.separator + "schemagen.sh";
				genCmd = jckBase + File.separator + "macos" + File.separator + "bin" + File.separator + "wsgen.sh";
				impCmd = jckBase + File.separator + "macos" + File.separator + "bin" + File.separator + "wsimport.sh";
			} else if (platform.equals("zos") || platform.equals("solaris")) {
				pathToJavac = testJdk + File.separator + "bin" + File.separator + "javac";
				xjcCmd = jckBase + File.separator + "solaris" + File.separator + "bin" + File.separator + "xjc.sh";
				jxcCmd = jckBase + File.separator + "solaris" + File.separator + "bin" + File.separator + "schemagen.sh";
				genCmd = jckBase + File.separator + "solaris" + File.separator + "bin" + File.separator + "wsgen.sh";
				impCmd = jckBase + File.separator + "solaris" + File.separator + "bin" + File.separator + "wsimport.sh";
			} else {
				throw new StfException("Unknown platform:: " + platform);
			}

                        if (platform.equals("linux")) {
				// bash required to run schema scripts on linux (cannot be standard sh)
				xjcCmd = "bash "+xjcCmd;
				jxcCmd = "bash "+jxcCmd;
                        }
			
			fileContent += "concurrency " + concurrencyString + ";\n";
			fileContent += "timeoutfactor 1" + ";\n";							// All Devtools tests take less than 1h to finish.
			
			if (platform.equals("win32")) {
				// On Windows set the testplatform.os to Windows and set systemRoot, but do not
				// set the file and path separators (an error is thrown if they are set).
				fileContent += "set jck.env.testPlatform.os \"Windows\";\n";
			}
			else {
				// On other platforms set the testplatform.os to other and set the file and path separators.
				fileContent += "set jck.env.testPlatform.os \"other\";\n";
				fileContent += "set jck.env.testPlatform.fileSep \"/\";\n";
				fileContent += "set jck.env.testPlatform.pathSep \":\";\n";
			}
			
			fileContent += "set jck.env.devtools.testExecute.cmdAsString \"" + pathToJava + "\"" + ";\n";
			fileContent += "set jck.env.devtools.refExecute.cmdAsFile \"" + pathToJava + "\"" + ";\n";
			
			fileContent += "set jck.env.devtools.scriptEnvVars \"" + "JAVA_HOME=\"" + testJdk + "\" TOOLS_HOME=\"" + testJdk + "\"" + "\"" + ";\n";
			
			if (tests.contains("java2schema")) {
				fileContent += "set jck.env.devtools.jaxb.jxcCmd \"" + jxcCmd + "\"" + ";\n";
			} else if (tests.contains("jaxws")) {
				fileContent += "set jck.env.devtools.jaxws.cmdJavac \"" + pathToJavac + "\"" + ";\n";
				fileContent += "set jck.env.devtools.jaxws.genCmd \"" + genCmd + "\"" + ";\n";
				fileContent += "set jck.env.devtools.jaxws.impCmd \"" + impCmd + "\"" + ";\n";
			} else {
				fileContent += "set jck.env.devtools.jaxb.xjcCmd \"" + xjcCmd + "\"" + ";\n";
			}
			
			// Get any additional jvm options for specific tests.
			extraJvmOptions += getTestSpecificJvmOptions(jckVersion, tests);

			extraJvmOptions += suppressOutOfMemoryDumpOptions;
			
			// Add the JVM options supplied by the user plus those added in this method to the jtb file option.
			fileContent += "set jck.env.devtools.refExecute.otherOpts \" " + extraJvmOptions + " \"" + ";\n";	
		}
	
	}
	
	private boolean testsRequireDisplay (String tests) {
		if (tests.equals("api") ||
			tests.contains("api/java_applet") ||
			tests.contains("api/javax_swing") || tests.contains("api/javax_sound") ||
			tests.contains("api/java_awt")  || tests.contains("api/javax_print") ||
			tests.contains("api/java_beans") || tests.contains("api/javax_accessibility") ||
			tests.contains("api/javax_naming")) {
			return true;
		}
		return false;
	}

	private String determineJckRoot (StfCoreExtension test, String jckVersion) throws StfException {

		DirectoryRef jckDir = null;   // This will be initialised when we find it.

		// Sub directories to look for the JCK
		ArrayList<String> jckTopDirs = new ArrayList<String>();
		jckTopDirs.add("jck/");
		jckTopDirs.add("");

		// Get the locations of the systemtest-prereq directories
		ArrayList<DirectoryRef> prereqRoots = test.env().getPrereqRoots();

		// Look for the JCK
		// Return the value of the first subdirectory where the JCK is found
		int found = 0;
		String jckTopDir = "";
		String returnJckTopDir = "";
		for (int i = 0 ; (i < jckTopDirs.size()); i++ ) {
			jckTopDir = jckTopDirs.get(i);
			for (int j = 0 ; (j < prereqRoots.size()); j++ ) {
				jckDir = prereqRoots.get(j).childDirectory(jckTopDir + jckVersion);
				if (!jckDir.exists()) {
					logger.info("Cannot find jckversion " + jckVersion + " at " + jckDir.getSpec());
				}
				else {
					logger.info("Found jckversion " + jckVersion + " at " + jckDir.getSpec());
					if ( found == 0 ) {
						returnJckTopDir = jckTopDir;
						found = 1;
					}
				}
			}
		}
		if ( found == 0 ) {
			throw new StfException(testExecutionType + "Cannot locate JCK version " + jckVersion + " in any of the directories referenced by -javatest-prereqs or under a jck subdirectory in one of those directories.");
		}
		return returnJckTopDir;
	}
	
	private String getTestSpecificJvmOptions (String jckVersion, String tests) {
		String testSpecificJvmOptions = "";
		Matcher matcher = Pattern.compile("jck(\\d+)c?").matcher(jckVersion);
		if (matcher.matches()) {
			// first group is going to be 8, 9, 10, 11, etc.
			int jckVerNum = Integer.parseInt(matcher.group(1));
			// --add-modules options are required to make some modules visible for Java 9 onwards.
			if (jckVerNum >= 9) {
				// If the top level api node is being run, add all modules required by the api tests
				if (tests.equals("api")) {
					testSpecificJvmOptions = " --add-modules java.xml.crypto,java.sql";
					if (jckVerNum < 11) {
						// following modules have been removed from Java 11 and onwards
						testSpecificJvmOptions += ",java.activation,java.corba,java.xml.ws.annotation,java.se.ee,java.transaction,java.xml.bind,java.xml.ws";
					}
				}
				if (tests.contains("api/javax_crypto") ) {
					testSpecificJvmOptions = " --add-modules java.xml.crypto";
				}
				if (tests.contains("api/javax_sql") ) {
					testSpecificJvmOptions = " --add-modules java.sql";
				}
				if (jckVerNum < 11) {
					if (tests.contains("api/javax_activation")) {
						testSpecificJvmOptions = " --add-modules java.activation";
					}
					if (tests.contains("api/javax_activity")) {
						testSpecificJvmOptions = " --add-modules java.corba";
					}
					if (tests.contains("api/javax_rmi")) {
						testSpecificJvmOptions = " --add-modules java.corba";
					}
					if (tests.contains("api/org_omg")) {
						testSpecificJvmOptions = " --add-modules java.corba";
					}
					if (tests.contains("api/javax_annotation")) {
						testSpecificJvmOptions = "--add-modules java.xml.ws.annotation";
					}
					if (tests.contains("api/java_lang")) {
						testSpecificJvmOptions = "--add-modules java.xml.ws.annotation,java.xml.bind,java.xml.ws,java.activation,java.corba";
					}
					if (tests.contains("api/javax_transaction") ) {
						testSpecificJvmOptions = " --add-modules java.transaction";
					}
					if (tests.contains("api/javax_xml") ) {
						testSpecificJvmOptions = " --add-modules java.xml.bind,java.xml.ws";
					}
					if (tests.contains("api/modulegraph")) {
						testSpecificJvmOptions = "--add-modules java.activation,java.corba,java.transaction,java.se.ee,java.xml.bind,java.xml.ws,java.xml.ws.annotation";
					}
					if (tests.contains("api/signaturetest")) {
						testSpecificJvmOptions = "--add-modules java.activation,java.corba,java.transaction,java.xml.bind,java.xml.ws,java.xml.ws.annotation";
					}
					if (tests.contains("java2schema") ) {
						testSpecificJvmOptions = " --add-modules java.xml.bind";
					}
					if (tests.contains("xml_schema") ) {
						testSpecificJvmOptions = " --add-modules java.xml.bind";
					}
					if (tests.contains("jaxws") ) {
						testSpecificJvmOptions = " --add-modules java.xml.bind,java.xml.ws";
					}
					if (tests.contains("schema2java") ) {
						testSpecificJvmOptions = " --add-modules java.xml.bind";
					}
					if (tests.contains("schema_bind") ) {
						testSpecificJvmOptions = " --add-modules java.xml.bind";
					}
				}
				testSpecificJvmOptions += " -Djdk.attach.allowAttachSelf=true";
			}
		} else {
			throw new Error("Unexpected jck version : " + jckVersion);
		}
		return testSpecificJvmOptions;
	}
}
