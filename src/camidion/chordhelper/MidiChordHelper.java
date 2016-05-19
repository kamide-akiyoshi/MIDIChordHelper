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
import java.awt.event.WindowListener;
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
import camidion.chordhelper.midieditor.MidiSequenceEditor;
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
				applet.deviceModelList.getEditorDialog().loadAndPlay(fileList);
			}
		});
	}
	private static class AppletFrame extends JFrame implements AppletStub, AppletContext {
		private JLabel status_ = new JLabel("Welcome to "+ChordHelperApplet.VersionInfo.NAME) {
			{ setFont(getFont().deriveFont(Font.PLAIN)); }
		};
		private ChordHelperApplet applet;
		private WindowListener windowListener = new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent evt) {
				MidiSequenceEditor ed = applet.deviceModelList.getEditorDialog();
				if( ! ed.sequenceListTable.getModel().isModified() || ed.confirm(
					"MIDI file not saved, exit anyway ?\n"+
					"保存されていないMIDIファイルがありますが、終了してよろしいですか？"
				)) System.exit(0);
			}
		};
		public AppletFrame(ChordHelperApplet applet) {
			this.applet = applet;
			setTitle(ChordHelperApplet.VersionInfo.NAME);
			add( applet, BorderLayout.CENTER );
			add( status_, BorderLayout.SOUTH );
			applet.setStub(this);
			applet.init();
			setIconImage(applet.iconImage);
			setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
			addWindowListener(windowListener);
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
			private MidiSequencerModel sequencerModel;
			/**
			 * タイトルバー更新器の構築
			 * @param applet 対象アプレット
			 */
			public TitleUpdater(ChordHelperApplet applet) {
				applet.deviceModelList.getEditorDialog().sequenceListTable.getModel().addTableModelListener(this);
				sequencerModel = applet.deviceModelList.getSequencerModel();
				sequencerModel.addChangeListener(this);
			}
			/**
			 * プレイリスト上で変更されたファイル名をタイトルバーに反映します。
			 */
			@Override
			public void tableChanged(TableModelEvent e) {
				int col = e.getColumn();
				if( col == PlaylistTableModel.Column.FILENAME.ordinal() || col == TableModelEvent.ALL_COLUMNS ) {
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
