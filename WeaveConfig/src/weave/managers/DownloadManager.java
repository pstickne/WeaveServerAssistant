package weave.managers;

import static weave.utils.TraceUtils.*;
import static weave.utils.TraceUtils.LEVEL.*;

import java.awt.Color;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidParameterException;

import javax.swing.JLabel;
import javax.swing.JProgressBar;

import weave.Settings;
import weave.async.AsyncCallback;
import weave.async.AsyncObserver;
import weave.async.AsyncFunction;
import weave.core.Function;
import weave.utils.BugReportUtils;
import weave.utils.DownloadUtils;
import weave.utils.FileUtils;
import weave.utils.StringUtils;
import weave.utils.TimerUtils;
import weave.utils.TransferUtils;
import weave.utils.ZipUtils;

public class DownloadManager 
{
	private String type = "";
	private String dlURLStr = "";
	private String dlFileStr = "";
	private String dlDestinationStr = "";
	private Function<Object, Object> callbackFunction = null;
	
	private JLabel label = null;
	private JProgressBar progressbar = null;
	
	private static DownloadManager _instance = null;
	public static DownloadManager init(String t)
	{
		if( _instance == null )
			_instance = new DownloadManager();
		
		_instance.type = t;
		_instance.dlURLStr = null;
		_instance.dlFileStr = null;
		_instance.dlDestinationStr = null;
		_instance.callbackFunction = null;
		
		_instance.label = null;
		_instance.progressbar = null;
		return _instance;
	}
	
	public DownloadManager setProgressbar(JProgressBar bar)
	{
		progressbar = bar;
		return this;
	}
	
	public DownloadManager setLabel(JLabel l)
	{
		label = l;
		return this;
	}
	
	public DownloadManager downloadFrom(String loc)
	{
		dlURLStr = loc;
		return this;
	}
	public DownloadManager extractTo(String loc)
	{
		dlFileStr = loc;
		return this;
	}
	public DownloadManager installTo(String loc)
	{
		dlDestinationStr = loc;
		return this;
	}
	public DownloadManager callback(Function<Object, Object> f)
	{
		callbackFunction = f;
		return this;
	}
	public void start() throws MalformedURLException, InterruptedException
	{
		if( dlURLStr != null )
			download();
		else if( dlFileStr != null )
			extract();
		else
			throw new InvalidParameterException("Nothing to download nor extract");
	}
	
	private void download() throws MalformedURLException, InterruptedException
	{
		final URL url = new URL(dlURLStr);
		final File file = new File(dlFileStr);

		final AsyncObserver observer = new AsyncObserver() {
			@Override
			public void onUpdate() {
				if( info.max == -1 ) {
					// Unknown max size - progress unavailable
					progressbar.setIndeterminate(true);
					if( label != null )
						label.setText( 
							String.format("Downloading " + type + ".... %s @ %s",
								FileUtils.sizeify(info.cur), 
								DownloadUtils.speedify(info.speed)) );
				} else {
					// Known max size
					progressbar.setIndeterminate(false);
					progressbar.setValue( info.percent );
					if( info.time > 3600 ) {
						if( label != null )
							label.setText(
								String.format("Downloading - %d%% - %s - %s (%s)", 
									info.percent, 
									"Calculating ETA...",
									FileUtils.sizeify(info.cur),
									DownloadUtils.speedify(info.speed)) );
					} else if( info.time < 60 ) {
						if( label != null )
							label.setText(
								String.format("Downloading - %d%% - %s - %s (%s)", 
									info.percent, 
									TimerUtils.format("%s s remaining", info.time),
									FileUtils.sizeify(info.cur),
									DownloadUtils.speedify(info.speed)) );
					} else {
						if( label != null )
							label.setText(
								String.format("Downloading - %d%% - %s - %s (%s)",
									info.percent, 
									TimerUtils.format("%m:%ss remaining", info.time),
									FileUtils.sizeify(info.cur),
									DownloadUtils.speedify(info.speed)) );
					}
				}
			}
		};
		final AsyncCallback callback = new AsyncCallback() {
			@Override
			public void run(Object o) {
				int returnCode = (Integer) o;

				Settings.transferCancelled = false;
				Settings.transferLocked = false;

				try {
					switch( returnCode )
					{
						case TransferUtils.COMPLETE:
							put(STDOUT, "DONE");
							if( label != null )
							{
								label.setText("Download Complete....");
								label.setForeground(Color.BLACK);
							}
							extract();
							break;
							
						case TransferUtils.CANCELLED:
							put(STDOUT, "CANCELLED");
							if( label != null )
							{
								label.setText("Cancelling Download....");
								label.setForeground(Color.BLACK);
							}							
							Thread.sleep(1000);
							callbackFunction.call(returnCode, dlFileStr);
							break;
							
						case TransferUtils.FAILED:
							put(STDOUT, "FAILED");
							if( label != null )
							{
								label.setText("Download Failed....");
								label.setForeground(Color.RED);
							}
							Thread.sleep(1000);
							callbackFunction.call(returnCode, dlFileStr);
							break;
							
						case TransferUtils.OFFLINE:
							put(STDOUT, "OFFLINE");
							if( label != null )
							{
								label.setText("Offline");
								label.setForeground(Color.BLACK);
							}
							Thread.sleep(1000);
							callbackFunction.call(returnCode, dlFileStr);
							break;
					}
				} catch (InterruptedException e) {
					trace(STDERR, e);
					BugReportUtils.showBugReportDialog(e);
				}
			}
		};
		AsyncFunction task = new AsyncFunction() {
			@Override
			public Object doInBackground() {
				int ret = TransferUtils.FAILED;
				try {
					observer.init(url);
					ret = DownloadUtils.download(url, file, observer, 6 * TransferUtils.MB);
				} catch (Exception e) {
					trace(STDERR, e);
					BugReportUtils.showBugReportDialog(e);
				}
				return ret;
			}
		};

		trace(STDOUT, INFO, StringUtils.rpad("Downloading " + type, ".", Settings.LOG_PADDING_LENGTH));
		
		label.setVisible(true);
		progressbar.setVisible(true);
		
		label.setText("Downloading " + type + "....");
		progressbar.setIndeterminate(true);
		
		Thread.sleep(1000);
		
		progressbar.setValue(0);
		progressbar.setIndeterminate(false);

		Settings.transferCancelled = false;
		Settings.transferLocked = true;

		task.addCallback(callback).call();
	}
	
	private void extract() throws InterruptedException
	{
		final File file = new File(dlFileStr);

		final AsyncObserver observer = new AsyncObserver() {
			@Override
			public void onUpdate() {
				if( progressbar != null )
					progressbar.setValue( info.percent / 2 );
				
				if( label != null )
					label.setText( 
						String.format(
							"Extracting " + type + ".... %d%%", 
							info.percent / 2 ) );
			}
		};
		AsyncCallback callback = new AsyncCallback() {
			@Override
			public void run(Object o) {
				int returnCode = (Integer) o;

				Settings.transferCancelled = false;
				Settings.transferLocked = false;

				try {
					switch( returnCode )
					{
						case TransferUtils.COMPLETE:
							put(STDOUT, "DONE");
							
							move();
							break;
						case TransferUtils.FAILED:
							put(STDOUT, "FAILED");
							label.setText("Extract Failed...");
							label.setForeground(Color.RED);
							
							Thread.sleep(1000);
							callbackFunction.call(returnCode, dlFileStr);
							break;
						case TransferUtils.CANCELLED:
							put(STDOUT, "CANCELLED");
							label.setText("Extract Cancelled...");
							label.setForeground(Color.BLACK);
							
							Thread.sleep(1000);
							callbackFunction.call(returnCode, dlFileStr);
							break;
						case TransferUtils.OFFLINE:
							put(STDOUT, "OFFLINE");
							label.setText("Offline");
							label.setForeground(Color.BLACK);

							Thread.sleep(1000);
							callbackFunction.call(returnCode, dlFileStr);
							break;
					}
				} catch (InterruptedException e) {
					trace(STDERR, e);
					BugReportUtils.showBugReportDialog(e);
				}
			}
		};
		AsyncFunction task = new AsyncFunction() {
			@Override
			public Object doInBackground() {
				Object o = TransferUtils.FAILED;
				try {
					observer.init(file);
					o = ZipUtils.extract(file, Settings.UNZIP_DIRECTORY, TransferUtils.OVERWRITE | TransferUtils.MULTIPLE_FILES, observer, 10 * TransferUtils.MB);
				} catch (NullPointerException e) {
					trace(STDERR, e);
					// No bug report
				} catch (Exception e) {
					trace(STDERR, e);
					BugReportUtils.showBugReportDialog(e);
				}
				return o;
			}
		};

		if( !Settings.UNZIP_DIRECTORY.exists() )
			Settings.UNZIP_DIRECTORY.mkdirs();
		
		trace(STDOUT, INFO, StringUtils.rpad("Extracting " + type, ".", Settings.LOG_PADDING_LENGTH));
		label.setVisible(true);
		progressbar.setVisible(true);
		
		label.setText("Extracting " + type + "....");
		progressbar.setIndeterminate(true);
		
		Thread.sleep(1000);
		
		progressbar.setValue(0);
		progressbar.setIndeterminate(false);
		
		Settings.transferCancelled = false;
		Settings.transferLocked = true;
		
		task.addCallback(callback).call();
	}
	
	private void move()
	{
		final File unzip = Settings.UNZIP_DIRECTORY;
		final File destination = new File(dlDestinationStr);
		
		final AsyncObserver observer = new AsyncObserver() {
			@Override
			public void onUpdate() {
				if( progressbar != null )
					progressbar.setValue( 50 + info.percent / 2 );
				
				if( label != null )
					label.setText( 
						String.format(
								"Installing " + type + ".... %d%%", 
								50 + info.percent / 2 ) );
			}
		};
		AsyncCallback callback = new AsyncCallback() {
			@Override
			public void run(Object o) {
				int returnCode = (Integer) o;

				Settings.transferCancelled = false;
				Settings.transferLocked = false;

				try {
					switch( returnCode ) 
					{
						case TransferUtils.COMPLETE:
							put(STDOUT, "DONE");
							label.setText("Install complete....");

							callbackFunction.call(returnCode, dlFileStr);
							break;
						case TransferUtils.FAILED:
							put(STDOUT, "FAILED");
							label.setText("Install Failed...");
							label.setForeground(Color.RED);
							
							Thread.sleep(1000);
							callbackFunction.call(returnCode, dlFileStr);
							break;
						case TransferUtils.CANCELLED:
							put(STDOUT, "CANCELLED");
							label.setText("Install Cancelled...");
							label.setForeground(Color.BLACK);
							
							Thread.sleep(1000);
							callbackFunction.call(returnCode, dlFileStr);
							break;
						case TransferUtils.OFFLINE:
							put(STDOUT, "OFFLINE");
							label.setText("Offline");
							label.setForeground(Color.BLACK);
	
							Thread.sleep(1000);
							callbackFunction.call(returnCode, dlFileStr);
							break;
					}
				} catch (InterruptedException e) {
					trace(STDERR, e);
					BugReportUtils.showBugReportDialog(e);
				}
			}
		};
		AsyncFunction task = new AsyncFunction() {
			@Override
			public Object doInBackground() {
				int status = TransferUtils.COMPLETE;
				String[] unzip_dir_files = unzip.list();
				try {
					if( unzip_dir_files.length == 1 )
					{
						File topLevelFile = new File(unzip, unzip_dir_files[0]);
						observer.init(topLevelFile);
						if( topLevelFile.isDirectory() )
						{
							String[] topLevelFile_dir_files = topLevelFile.list();
							for( String name : topLevelFile_dir_files )
							{
								File s = new File(topLevelFile, name);
								File d = new File(destination, name);
								status &= FileUtils.copy(s, d, TransferUtils.MULTIPLE_FILES | TransferUtils.OVERWRITE, observer, 8 * TransferUtils.MB);
							}
						}
						else
						{
							File s = new File(unzip, topLevelFile.getName());
							File d = new File(destination, topLevelFile.getName());
							status &= FileUtils.copy(s, d, TransferUtils.MULTIPLE_FILES | TransferUtils.OVERWRITE, observer, 8 * TransferUtils.MB);
						}
					}
					else
					{
						observer.init(unzip);
						
						for( String file : unzip_dir_files )
						{
							File s = new File(unzip, file);
							File d = new File(destination, file);
							status &= FileUtils.copy(s, d, TransferUtils.MULTIPLE_FILES | TransferUtils.OVERWRITE, observer, 10 * TransferUtils.MB);
						}
					}
				} catch (Exception e) {
					trace(STDERR, e);
					BugReportUtils.showBugReportDialog(e);
				}
				return status;
			}
		};

		trace(STDOUT, INFO, StringUtils.rpad("Installing " + type, ".", Settings.LOG_PADDING_LENGTH));

		label.setVisible(true);
		progressbar.setVisible(true);
		
		label.setText("Installing " + type + "....");
		progressbar.setIndeterminate(false);

		Settings.transferCancelled = false;
		Settings.transferLocked = true;

		task.addCallback(callback).call();
	}
}
