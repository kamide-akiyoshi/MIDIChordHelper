package camidion.chordhelper;

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
import java.util.List;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import camidion.chordhelper.mididevice.MidiSequencerModel;
import camidion.chordhelper.midieditor.PlaylistTableModel;
import camidion.chordhelper.midieditor.SequenceTrackListTableModel;

/**
 * MIDI Chord Helper を Java アプリとして起動します。
 */
public class MidiChordHelper {
	private static int count = 0;
	private static AppletFrame frame = null;
	private static List<File> fileList = new Vector<File>();
	/**
	 * MIDI Chord Helper を Java アプリとして起動します。
	 * @param args コマンドライン引数
	 * @throws Exception 何らかの異常が発生した場合にスローされる
	 */
	public static void main(String[] args) throws Exception {
		if( args.length > 0 ) {
			for( String arg : args ) fileList.add(new File(arg));
		}
		SwingUtilities.invokeLater(new Runnable(){
			@Override
			public void run() {
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
				applet.deviceModelList.editorDialog.loadAndPlay(fileList);
			}
		});
	}
	private static class AppletFrame extends JFrame
		implements AppletStub, AppletContext
	{
		JLabel status_;
		ChordHelperApplet applet;
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
			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent evt) {
					if( AppletFrame.this.applet.isConfirmedToExit() )
						System.exit(0);
				}
			});
			new TitleUpdater(applet);
			pack();
			setLocationRelativeTo(null);
			setVisible(true);
			applet.start();
		}
		/**
		 * タイトルバー更新器
		 */
		private class TitleUpdater implements ChangeListener, TableModelListener {
			MidiSequencerModel sequencerModel;
			/**
			 * タイトルバー更新器の構築
			 * @param applet 対象アプレット
			 */
			public TitleUpdater(ChordHelperApplet applet) {
				applet.deviceModelList.editorDialog.sequenceListTable.getModel().addTableModelListener(this);
				sequencerModel = applet.deviceModelList.getSequencerModel();
				sequencerModel.addChangeListener(this);
			}
			/**
			 * プレイリスト上で変更されたファイル名をタイトルバーに反映します。
			 */
			@Override
			public void tableChanged(TableModelEvent e) {
				int col = e.getColumn();
				if( col == PlaylistTableModel.Column.FILENAME.ordinal() ) {
					setFilenameToTitle();
				}
				if( col == TableModelEvent.ALL_COLUMNS ) {
					setFilenameToTitle();
				}
			}
			/**
			 * 再生中にファイルが切り替わったら、そのファイル名をタイトルバーに反映します。
			 */
			@Override
			public void stateChanged(ChangeEvent e) { setFilenameToTitle(); }
			/**
			 * シーケンサーにロードされている曲のファイル名をタイトルバーに反映します。
			 */
			private void setFilenameToTitle() {
				SequenceTrackListTableModel seq = sequencerModel.getSequenceTrackListTableModel();
				String filename = ( seq == null ? null : seq.getFilename() );
				String title = ChordHelperApplet.VersionInfo.NAME;
				if( filename != null && ! filename.isEmpty() ) {
					title = filename + " - " + title;
				}
				setTitle(title);
			}
		}
		@Override
		public boolean isActive() { return true; }
		@Override
		public URL getDocumentBase() { return null; }
		@Override
		public URL getCodeBase() { return null; }
		@Override
		public String getParameter(String name) { return null; }
		@Override
		public AppletContext getAppletContext() { return this; }
		@Override
		public void appletResize(int width, int height) {}
		@Override
		public AudioClip getAudioClip(URL url) { return null; }
		@Override
		public Image getImage(URL url) {
			return Toolkit.getDefaultToolkit().getImage(url);
		}
		@Override
		public Applet getApplet(String name) { return null; }
		@Override
		public Enumeration<Applet> getApplets() { return (null); }
		@Override
		public void showDocument(URL url) {}
		@Override
		public void showDocument(URL url, String target) {}
		@Override
		public void showStatus(String status) { status_.setText(status); }
		@Override
		public InputStream getStream(String key) { return null; }
		@Override
		public Iterator<String> getStreamKeys() { return null; }
		@Override
		public void setStream(String key, InputStream stream) throws IOException {}
	}
}
