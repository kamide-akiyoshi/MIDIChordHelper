package camidion.chordhelper;

import java.applet.Applet;
import java.applet.AppletContext;
import java.applet.AppletStub;
import java.applet.AudioClip;
import java.awt.BorderLayout;
import java.awt.Font;
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
	private static List<File> fileList = new Vector<File>();
	/**
	 * MIDI Chord Helper を Java アプリとして起動します。
	 * @param args コマンドライン引数
	 * @throws Exception 何らかの異常が発生した場合にスローされる
	 */
	public static void main(String[] args) throws Exception {
		for( String arg : args ) fileList.add(new File(arg));
		SwingUtilities.invokeLater(new Runnable(){
			@Override
			public void run() { new AppletFrame(new ChordHelperApplet(), fileList); }
		});
	}
	private static class AppletFrame extends JFrame implements AppletStub, AppletContext {
		private JLabel status_ = new JLabel("Welcome to "+ChordHelperApplet.VersionInfo.NAME) {
			{ setFont(getFont().deriveFont(Font.PLAIN)); }
		};
		/**
		 * 指定されたMIDIシーケンスモデルのファイル名をタイトルバーに反映します。
		 * @param seq MIDIシーケンスモデル
		 */
		private void setFilenameToTitle(SequenceTrackListTableModel seq) {
			String title = ChordHelperApplet.VersionInfo.NAME;
			if( seq != null ) {
				String filename = seq.getFilename();
				if( filename != null && ! filename.isEmpty() ) title = filename + " - " + title;
			}
			setTitle(title);
		}
		public AppletFrame(final ChordHelperApplet applet, List<File> fileList) {
			setTitle(ChordHelperApplet.VersionInfo.NAME);
			add( applet, BorderLayout.CENTER );
			add( status_, BorderLayout.SOUTH );
			applet.setStub(this);
			applet.init();
			setIconImage(applet.iconImage);
			setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
			pack();
			setLocationRelativeTo(null);
			setVisible(true);
			applet.start();
			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent event) {
					if( ! applet.playlistModel.isModified() || applet.midiEditor.confirm(
						"MIDI file not saved, exit anyway ?\n"+
						"保存されていないMIDIファイルがありますが、終了してよろしいですか？"
					)) {
						applet.destroy();
						System.exit(0);
					}
				}
			});
			applet.sequencerModel.addChangeListener(new ChangeListener() {
				/**
				 * シーケンサで切り替わった再生対象ファイル名をタイトルバーに反映
				 */
				@Override
				public void stateChanged(ChangeEvent event) {
					MidiSequencerModel sequencerModel = (MidiSequencerModel) event.getSource();
					setFilenameToTitle(sequencerModel.getSequenceTrackListTableModel());
				}
			});
			applet.playlistModel.addTableModelListener(new TableModelListener() {
				/**
				 * プレイリスト上で変更された再生対象ファイル名をタイトルバーに反映
				 */
				@Override
				public void tableChanged(TableModelEvent event) {
					int col = event.getColumn();
					if( col == PlaylistTableModel.Column.FILENAME.ordinal() || col == TableModelEvent.ALL_COLUMNS ) {
						PlaylistTableModel pl = (PlaylistTableModel) event.getSource();
						setFilenameToTitle(pl.getSequencerModel().getSequenceTrackListTableModel());
					}
				}
			});
			applet.midiEditor.loadAndPlay(fileList);
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
