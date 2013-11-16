
import java.applet.Applet;
import java.applet.AppletContext;
import java.applet.AppletStub;
import java.applet.AudioClip;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

/**
 * MIDI Chord Helper を Java アプリとして起動します。
 */
public class MidiChordHelper {
	static int count = 0;
	static AppletFrame frame = null;
	/**
	 * MIDI Chord Helper を Java アプリとして起動します。
	 * @param args コマンドライン引数
	 * @throws Exception 何らかの異常が発生した場合にスローされる
	 */
	public static void main(String[] args) throws Exception {
		ChordHelperApplet applet;
		if( count++ > 0 && frame != null) {
			applet = frame.applet;
			int windowState = frame.getExtendedState();
			if( ( windowState & Frame.ICONIFIED ) == 0 ) {
				frame.toFront();
			} else {
				frame.setExtendedState(windowState &= ~(Frame.ICONIFIED));
			}
		} else {
			frame = new AppletFrame(applet = new ChordHelperApplet());
		}
		if( args.length > 0 ) {
			Vector<File> fileList = new Vector<File>();
			for( String arg : args ) fileList.add(new File(arg));
			applet.editorDialog.loadAndPlay(fileList);
		}
	}
}

class AppletFrame extends JFrame implements
	AppletStub, AppletContext, ChangeListener, TableModelListener
{
	JLabel status_;
	ChordHelperApplet applet = null;
	public AppletFrame(ChordHelperApplet applet) {
		setTitle(ChordHelperApplet.VersionInfo.NAME);
		(status_ = new JLabel()).setFont(
			status_.getFont().deriveFont(Font.PLAIN)
		);
		add( this.applet = applet, BorderLayout.CENTER );
		add( status_, BorderLayout.SOUTH );
		applet.setStub(this);
		applet.init();
		Image iconImage = applet.imageIcon == null ? null : applet.imageIcon.getImage();
		setIconImage(iconImage);
		setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
		addWindowListener( new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent evt) {
				if( AppletFrame.this.applet.isConfirmedToExit() )
					System.exit(0);
			}
		});
		// シーケンスファイル名の変更監視
		applet.editorDialog.sequenceListTableModel.addTableModelListener(this);
		applet.deviceModelList.sequencerModel.addChangeListener(this);
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
		applet.start();
	}
	/**
	 * プレイリストで変更されたファイル名をタイトルバーに反映します。
	 */
	@Override
	public void tableChanged(TableModelEvent e) {
		if( e.getColumn() == SequenceListTableModel.Column.FILENAME.ordinal() )
			setFilenameToTitle();
	}
	@Override
	public void stateChanged(ChangeEvent e) {
		setFilenameToTitle();
	}
	private void setFilenameToTitle() {
		MidiSequenceTableModel seqModel = applet.deviceModelList.sequencerModel.getSequenceTableModel();
		String filename = ( seqModel == null ? null : seqModel.getFilename() );
		String title = ChordHelperApplet.VersionInfo.NAME;
		if( filename != null && ! filename.isEmpty() ) {
			title = filename + " - " + title;
		}
		setTitle(title);
	}
	@Override
	public boolean isActive() { return true; }
	public URL getDocumentBase() { return null; }
	public URL getCodeBase() { return null; }
	public String getParameter(String name) { return null; }
	public AppletContext getAppletContext() { return this; }
	public void appletResize(int width, int height) {}
	public AudioClip getAudioClip(URL url) { return null; }
	public Image getImage(URL url) {
		return Toolkit.getDefaultToolkit().getImage(url);
	}
	public Applet getApplet(String name) { return null; }
	public Enumeration<Applet> getApplets() { return (null); }
	public void showDocument(URL url) {}
	public void showDocument(URL url, String target) {}
	public void showStatus(String status) { status_.setText(status); }
	public InputStream getStream(String key) { return null; }
	public Iterator<String> getStreamKeys() { return null; }
	public void setStream(String key, InputStream stream) throws IOException {}
}
