package weave.managers;

import static weave.utils.TraceUtils.STDERR;
import static weave.utils.TraceUtils.STDOUT;
import static weave.utils.TraceUtils.put;
import static weave.utils.TraceUtils.trace;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.ZipException;

import javax.swing.JLabel;
import javax.swing.JProgressBar;

import weave.Function;
import weave.Settings;
import weave.async.AsyncCallback;
import weave.async.AsyncObserver;
import weave.async.AsyncTask;
import weave.utils.BugReportUtils;
import weave.utils.DownloadUtils;
import weave.utils.FileUtils;
import weave.utils.TimeUtils;
import weave.utils.TransferUtils;
import weave.utils.ZipUtils;

public class DownloadManager 
{
	public static void download(String urlStr, String fileStr, String destinationStr, final JProgressBar progressbar, final JLabel label, final Function func) throws MalformedURLException, InterruptedException
	{
		final URL url = new URL(urlStr);
		final File file = new File(fileStr);
		final File destination = new File(destinationStr);
		
		AsyncCallback callback = new AsyncCallback() {
			@Override
			public void run(Object o) {
				int returnCode = (Integer) o;

				Settings.transferCancelled = false;
				Settings.downloadLocked = false;

				try {
					switch( returnCode )
					{
						case TransferUtils.COMPLETE:
							put(STDOUT, "DONE");
							label.setText("Download Complete....");
							label.setForeground(Color.BLACK);
		
							extract(file, destination, progressbar, label, func);
							break;
						case TransferUtils.CANCELLED:
							put(STDOUT, "CANCELLED");
							label.setText("Cancelling Download....");
							label.setForeground(Color.BLACK);
							
							Thread.sleep(1000);
							func.call();
							break;
						case TransferUtils.FAILED:
							put(STDOUT, "FAILED");
							label.setText("Download Failed....");
							label.setForeground(Color.RED);
							
							Thread.sleep(1000);
							func.call();
							break;
						case TransferUtils.OFFLINE:
							put(STDOUT, "OFFLINE");
							label.setText("Offline");
							label.setForeground(Color.BLACK);
							
							Thread.sleep(1000);
							func.call();
							break;
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		};
		final AsyncObserver observer = new AsyncObserver() {
			@Override
			public void onUpdate() {
				if( info.max == -1 ) {
					// Unknown max size - progress unavailable
					progressbar.setIndeterminate(true);
					label.setText( 
						String.format("Downloading update.... %s @ %s",
							FileUtils.sizeify(info.cur), 
							DownloadUtils.speedify(info.speed)) );
				} else {
					// Known max size
					progressbar.setIndeterminate(false);
					progressbar.setValue( info.percent );
					if( info.time > 3600 )
						label.setText(
							String.format("Downloading - %d%% - %s - %s (%s)", 
								info.percent, 
								"Calculating ETA...",
								FileUtils.sizeify(info.cur),
								DownloadUtils.speedify(info.speed)) );
					else if( info.time < 60 )
						label.setText(
							String.format("Downloading - %d%% - %s - %s (%s)", 
								info.percent, 
								TimeUtils.format("%s s remaining", info.time),
								FileUtils.sizeify(info.cur),
								DownloadUtils.speedify(info.speed)) );
					else
						label.setText(
							String.format("Downloading - %d%% - %s - %s (%s)",
								info.percent, 
								TimeUtils.format("%m:%ss remaining", info.time),
								FileUtils.sizeify(info.cur),
								DownloadUtils.speedify(info.speed)) );
				}
			}
		};
		AsyncTask task = new AsyncTask() {
			@Override
			public Object doInBackground() {
				int ret = TransferUtils.FAILED;
				try {
					observer.init(url);
					ret = DownloadUtils.download(url, file, observer, 2 * TransferUtils.MB);
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				return ret;
			}
		};

		trace(STDOUT, "-> Downloading plugin.............");
		
		label.setVisible(true);
		progressbar.setVisible(true);
		
		label.setText("Downloading plugin.....");
		progressbar.setIndeterminate(true);
		
		Thread.sleep(1000);
		
		progressbar.setValue(0);
		progressbar.setIndeterminate(false);

		Settings.transferCancelled = false;
		Settings.downloadLocked = true;

		task.addCallback(callback).execute();
	}
	
	public static void extract(final File zipFile, final File destination, final JProgressBar progressbar, final JLabel label, final Function func)
	{
		AsyncCallback callback = new AsyncCallback() {
			@Override
			public void run(Object o) {
				int returnCode = (Integer) o;
				
				switch( returnCode )
				{
					case TransferUtils.COMPLETE:
						put(STDOUT, "DONE");
						
						move(Settings.UNZIP_DIRECTORY, destination, progressbar, label, func);
						break;
					case TransferUtils.FAILED:
						put(STDOUT, "FAILED");
						label.setText("Extract Failed...");
						label.setForeground(Color.RED);
						break;
					case TransferUtils.CANCELLED:
						put(STDOUT, "CANCELLED");
						label.setText("Extract Cancelled...");
						label.setForeground(Color.BLACK);
						break;
					case TransferUtils.OFFLINE:
						put(STDOUT, "OFFLINE");
						label.setText("Offline");
						label.setForeground(Color.BLACK);
						break;
				}
			}
		};
		final AsyncObserver observer = new AsyncObserver() {
			@Override
			public void onUpdate() {
				progressbar.setValue( info.percent / 2 );
				label.setText( 
					String.format(
						"Extracting update.... %d%%", 
						info.percent / 2 ) );
			}
		};
		AsyncTask task = new AsyncTask() {
			@Override
			public Object doInBackground() {
				Object o = TransferUtils.FAILED;
				try {
					observer.init(zipFile);
					o = ZipUtils.extract(zipFile, Settings.UNZIP_DIRECTORY, TransferUtils.OVERWRITE | TransferUtils.MULTIPLE_FILES, observer, 8 * TransferUtils.MB);
				} catch (ArithmeticException e) {
					trace(STDERR, e);
					BugReportUtils.showBugReportDialog(e);
				}catch (NullPointerException e) {
					trace(STDERR, e);
					// No bug report
				} catch (ZipException e) {
					trace(STDERR, e);
					BugReportUtils.showBugReportDialog(e);
				} catch (IOException e) {
					trace(STDERR, e);
					BugReportUtils.showBugReportDialog(e);
				} catch (InterruptedException e) {
					trace(STDERR, e);
					BugReportUtils.showBugReportDialog(e);
				}
				return o;
			}
		};
		
		task.addCallback(callback).execute();
	}
	
	public static void move(File source, File destination, JProgressBar progressbar, JLabel label, Function func)
	{
		
	}
}