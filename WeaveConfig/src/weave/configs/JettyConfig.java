/*
    Weave (Web-based Analysis and Visualization Environment)
    Copyright (C) 2008-2014 University of Massachusetts Lowell

    This file is a part of Weave.

    Weave is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License, Version 3,
    as published by the Free Software Foundation.

    Weave is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Weave.  If not, see <http://www.gnu.org/licenses/>.
*/

package weave.configs;

import static weave.utils.TraceUtils.*;
import static weave.utils.TraceUtils.LEVEL.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

import weave.Settings;
import weave.async.AsyncCallback;
import weave.async.AsyncFunction;
import weave.managers.ConfigManager;
import weave.managers.ResourceManager;
import weave.utils.BugReportUtils;
import weave.utils.ObjectUtils;
import weave.utils.ProcessUtils;
import weave.utils.RemoteUtils;
import weave.utils.StringUtils;
import weave.utils.SyscallCreatorUtils;
import weave.utils.TransferUtils;

public class JettyConfig extends Config
{
	public static final String NAME = "Jetty";
	public static final String HOMEPAGE = "http://eclipse.org/jetty/";
	public static final String HOST = "localhost";
	public static final int PORT = 8084;
	public static final String DESCRIPTION 	= "Jetty provides an HTTP server and servlet container capable of serving static and " +
											  "dynamic content either from standalone or embedded instantiations.<br>" +
											  "Jetty is a free and open-source project as part of the Eclipse Foundation&#153.";
	public static final String WARNING 		= "<center><b>Jetty is a plugin that will run inside the tool and does not require external configuration.<br>" + 
										 	  "This is the appropriate choice for new users.</b></center>";
	
	public static JettyConfig _instance = null;
	public static JettyConfig getConfig()
	{
		if( _instance == null )
			_instance = new JettyConfig();
		return _instance;
	}
	
	public JettyConfig()
	{
		super(NAME, HOST, PORT);
	}

	@Override public void initConfig()
	{
		super.initConfig(_HOST | _PORT | _VERSION);
		
		File thisPluginDir = new File(Settings.DEPLOYED_PLUGINS_DIRECTORY, CONFIG_NAME);
		try {
			setWebappsDirectory(new File(thisPluginDir, "webapps"));
			setHomepageURL(HOMEPAGE);
			setDownloadURL(RemoteUtils.getConfigEntry(RemoteUtils.JETTY_URL));
			setDescription(DESCRIPTION);
			setWarning(WARNING);
			setImage(ImageIO.read(ResourceManager.IMAGE_JETTY));
			
		} catch (IOException e) {
			trace(STDERR, e);
			BugReportUtils.showBugReportDialog(e);
		}
	}
	
	@Override public boolean loadConfig() 
	{
		boolean result = ConfigManager.getConfigManager().setContainer(_instance);
		
		try {
			if( !Settings.SLOCK_FILE.exists() )
				return false;
			
			if( result ) {
				startServer();
				super.loadConfig();
			}
			else
				JOptionPane.showMessageDialog(null, 
						"There was an error loading the " + getConfigName() + " config.\n" + 
						"Another config might already be loaded.", 
						"Error", JOptionPane.ERROR_MESSAGE);
		} catch (NumberFormatException e) {
			trace(STDERR, e);
			BugReportUtils.showBugReportDialog(e);
		}
		
		return result;
	}

	@Override public boolean unloadConfig() 
	{
		boolean result = ConfigManager.getConfigManager().setContainer(null);
		stopServer();
		super.unloadConfig();
		return result;
	}
	
	
	public void startServer()
	{
		final AsyncFunction startTask = new AsyncFunction("Jetty Server running on " + getPort()) {
			@Override
			public Object doInBackground() {
				Object o = TransferUtils.FAILED;

				trace(STDOUT, INFO, StringUtils.rpad("Starting " + getConfigName() + " server", ".", Settings.LOG_PADDING_LENGTH));

				try {
					String basePath = (String)ObjectUtils.ternary(getWebappsDirectory(), "getAbsolutePath", "") + "/../";
					File logStdout = new File(basePath + Settings.F_S + "logs" + Settings.F_S, getLogFile(STDOUT).getName());
					File logStderr = new File(basePath + Settings.F_S + "logs" + Settings.F_S, getLogFile(STDERR).getName());
					String cmd = 	"java -jar -Xmx1024m \"" + basePath + "start.jar\" " +
									"jetty.logs=\"" + basePath + "/logs/\" " +
									"jetty.home=\"" + basePath + "\" " +
									"jetty.base=\"" + basePath + "\" " +
									"jetty.port=" + getPort() + " " +
									"STOP.PORT=" + (getPort()+1) + " STOP.KEY=jetty";
					String[] START = SyscallCreatorUtils.generate(cmd);
					
					o = ProcessUtils.run(START, logStdout, logStderr);
				} catch (Exception e) {
					trace(STDERR, e);
					BugReportUtils.showBugReportDialog(e);
				}
				return o;
			}
		};
		AsyncCallback stopCallback = new AsyncCallback() {
			@Override
			public void run(Object o) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					trace(STDERR, e);
				}
				startTask.call();
			}
		};
		AsyncFunction stopTask = new AsyncFunction() {
			@Override
			public Object doInBackground() {
				stopServer();
				return null;
			}
		};
		
		// If the service is already running, try to stop it first.
		// Might have been caused by a previous improper shutdown
		if( Settings.isServiceUp(getHost(), getPort()) )
		{
			stopTask.addCallback(stopCallback).call();
		}
		else
		{
			startTask.call();
		}
	}
	public static String stop()
	{
		return getConfig().stopServer().toString();
	}
	public Map<String, List<String>> stopServer()
	{
		trace(STDOUT, INFO, StringUtils.rpad("Stopping " + getConfigName() + " server", ".", Settings.LOG_PADDING_LENGTH));

		try {
			String basePath = (String)ObjectUtils.ternary(getWebappsDirectory(), "getAbsolutePath", "") + "/../";
			String cmd = 	"java -jar \"" + basePath + "start.jar\" " +
							"jetty.base=\"" + basePath + "\" " +
							"STOP.PORT=" + (_port+1) + " STOP.KEY=jetty --stop";
			String[] STOP = SyscallCreatorUtils.generate(cmd);
			
			return ProcessUtils.run(STOP);
		} catch (Exception e) {
			trace(STDERR, e);
			BugReportUtils.showBugReportDialog(e);
		}
		return null;
	}
}
