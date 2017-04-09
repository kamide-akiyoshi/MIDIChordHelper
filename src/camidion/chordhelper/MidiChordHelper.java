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
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.TableModelEvent;

import camidion.chordhelper.mididevice.MidiSequencerModel;
import camidion.chordhelper.midieditor.PlaylistTableModel;
import camidion.chordhelper.midieditor.SequenceTrackListTableModel;

/**
 * MIDI Chord Helper を Java アプリとして起動します。
 */
public class MidiChordHelper {
	/**
	 * MIDI Chord Helper を Java アプリとして起動します。
	 * @param args コマンドライン引数
	 * @throws Exception 何らかの異常が発生した場合にスローされる
	 */
	public static void main(String[] args) throws Exception {
		List<File> fileList = Arrays.asList(args).stream().map(arg -> new File(arg)).collect(Collectors.toList());
		SwingUtilities.invokeLater(()->new AppletFrame(new ChordHelperApplet(), fileList));
	}
	private static class AppletFrame extends JFrame implements AppletStub, AppletContext {
		private JLabel status_ = new JLabel("Welcome to "+ChordHelperApplet.VersionInfo.NAME) {
			{ setFont(getFont().deriveFont(Font.PLAIN)); }
		};
		private void updateFilename(SequenceTrackListTableModel sequence) {
			String title = ChordHelperApplet.VersionInfo.NAME;
			if( sequence != null ) {
				String filename = sequence.getFilename();
				if( filename != null && ! filename.isEmpty() )
					title = filename+" - "+title;
			}
			setTitle(title);
		}
		private void updateFilename(MidiSequencerModel sequencer) {
			updateFilename(sequencer.getSequenceTrackListTableModel());
		}
		private void updateFilename(TableModelEvent event) {
			if( ! PlaylistTableModel.filenameChanged(event) ) return;
			updateFilename(((PlaylistTableModel)event.getSource()).getSequencerModel());
		}
		public AppletFrame(ChordHelperApplet applet, List<File> fileList) {
			add( applet, BorderLayout.CENTER );
			add( status_, BorderLayout.SOUTH );
			applet.setStub(this);
			applet.init();
			setIconImage(applet.getIconImage());
			pack();
			setLocationRelativeTo(null);
			setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent event) {
					if( ! applet.isModified() || applet.midiEditor.confirm(
						"MIDI file not saved, exit anyway ?\n"+
						"保存されていないMIDIファイルがありますが、終了してよろしいですか？"
					)) { applet.destroy(); System.exit(0); }
				}
			});
			applet.playlistModel.getSequencerModel().addChangeListener(e->updateFilename((MidiSequencerModel)e.getSource()));
			applet.playlistModel.addTableModelListener(e->updateFilename(e));
			updateFilename(applet.playlistModel.getSequencerModel());
			setVisible(true);
			applet.start();
			applet.midiEditor.play(fileList);
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
