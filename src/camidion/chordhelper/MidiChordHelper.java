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
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.TableModelEvent;

import camidion.chordhelper.mididevice.MidiSequencerModel;
import camidion.chordhelper.midieditor.PlaylistTableModel;
import camidion.chordhelper.midieditor.SequenceTrackListTableModel;

/**
 * MIDI Chord Helper を Java アプリとして起動します。
 */
public class MidiChordHelper extends JFrame implements AppletStub, AppletContext {
	/**
	 * MIDI Chord Helper を Java アプリとして起動します。
	 * @param args コマンドライン引数
	 * @throws Exception 何らかの異常が発生した場合にスローされる
	 */
	public static void main(String[] args) throws Exception {
		List<File> fileList = Arrays.asList(args).stream()
				.map(arg -> new File(arg))
				.collect(Collectors.toList());
		SwingUtilities.invokeLater(()->new MidiChordHelper(fileList));
	}
	private static boolean confirmBeforeExit() {
		String message = "MIDI file not saved, exit anyway ?\n"+
				"MIDIファイルが保存されていません。終了してよろしいですか？";
		return JOptionPane.showConfirmDialog(
				null, message, ChordHelperApplet.VersionInfo.NAME,
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION ;
	}
	private JLabel statusBar = new JLabel("Welcome to "+ChordHelperApplet.VersionInfo.NAME) {
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
	private MidiChordHelper(List<File> fileList) {
		ChordHelperApplet applet = new ChordHelperApplet();
		add(applet, BorderLayout.CENTER);
		add(statusBar, BorderLayout.SOUTH);
		applet.setStub(this);
		applet.init();
		setIconImage(applet.getIconImage());
		pack();
		setLocationRelativeTo(null);
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				if( applet.isModified() && ! confirmBeforeExit() ) return;
				applet.destroy();
				System.exit(0);
			}
		});
		PlaylistTableModel playlist = applet.midiEditor.getPlaylistModel();
		MidiSequencerModel sequencer = playlist.getSequencerModel();
		sequencer.addChangeListener(e->updateFilename((MidiSequencerModel)e.getSource()));
		playlist.addTableModelListener(e->updateFilename(e));
		updateFilename(sequencer);
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
	public void showStatus(String status) { statusBar.setText(status); }
	@Override
	public InputStream getStream(String key) { return null; }
	@Override
	public Iterator<String> getStreamKeys() { return null; }
	@Override
	public void setStream(String key, InputStream stream) throws IOException {}
}
