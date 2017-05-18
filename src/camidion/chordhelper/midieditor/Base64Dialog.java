package camidion.chordhelper.midieditor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.regex.Pattern;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import camidion.chordhelper.ButtonIcon;
import camidion.chordhelper.ChordHelperApplet;
import camidion.chordhelper.music.MIDISpec;

/**
 * Base64テキスト入力ダイアログ
 */
public class Base64Dialog extends JDialog implements DocumentListener {
	public static final Pattern HEADER_PATTERN = Pattern.compile("^.*:.*$", Pattern.MULTILINE);
	private JTextArea base64TextArea = new JTextArea(8,56);
	private void error(String message) {
		JOptionPane.showMessageDialog(base64TextArea, (Object)message,
				ChordHelperApplet.VersionInfo.NAME, JOptionPane.WARNING_MESSAGE);
		base64TextArea.requestFocusInWindow();
	}
	private String createHeader(String filename) {
		return "Content-Type: audio/midi; name=\"" + filename + "\"\n"
				+ "Content-Transfer-Encoding: base64\n\n";
	}
	public PlaylistTable playlistTable;
	/**
	 * 入力されたBase64テキストをデコードし、MIDIシーケンスとしてプレイリストに追加します。
	 * @return プレイリストに追加されたMIDIシーケンスのインデックス（先頭が0）、追加に失敗した場合は -1
	 */
	public int addToPlaylist() {
		byte[] midiData = null;
		try {
			midiData = getMIDIData();
		} catch(Exception e) {
			error("Base64デコードに失敗しました。\n"+e);
			return -1;
		}
		try (InputStream in = new ByteArrayInputStream(midiData)) {
			Sequence sequence = MidiSystem.getSequence(in);
			Charset charset = MIDISpec.getCharsetOf(sequence);
			if( charset == null ) charset = Charset.defaultCharset();
			int index = playlistTable.getModel().add(sequence, charset, null);
			playlistTable.getSelectionModel().setSelectionInterval(index, index);
			return index;
		} catch( IOException|InvalidMidiDataException e ) {
			error("Base64デコードした結果をMIDIシーケンスとして読み込めませんでした。\n"+e);
			return -1;
		}
	}
	/**
	 * Base64デコードアクション
	 */
	public Action addBase64Action = new AbstractAction("Add to PlayList", new ButtonIcon(ButtonIcon.EJECT_ICON)) {
		{ putValue(Action.SHORT_DESCRIPTION, "Base64デコードして、プレイリストへ追加"); }
		@Override
		public void actionPerformed(ActionEvent e) {
			if( addToPlaylist() >= 0 ) setVisible(false);
		}
	};
	/**
	 * Base64テキストクリアアクション
	 */
	public Action clearAction = new AbstractAction("Clear", new ButtonIcon(ButtonIcon.X_ICON)) {
		{ putValue(Action.SHORT_DESCRIPTION, "Base64テキスト欄を消去"); }
		@Override
		public void actionPerformed(ActionEvent e) { setText(null); }
	};
	/**
	 * Base64テキスト入力ダイアログを構築します。
	 * @param playlistTable Base64デコードされたMIDIシーケンスの追加先プレイリストビュー
	 */
	public Base64Dialog(PlaylistTable playlistTable) {
		this.playlistTable = playlistTable;
		setTitle("Base64-encoded MIDI sequence - " + ChordHelperApplet.VersionInfo.NAME);
		add(new JPanel() {{
			setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
			add(new JPanel() {{
				setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
				add(Box.createRigidArea(new Dimension(10, 0)));
				add(new JButton(addBase64Action){{setMargin(ChordHelperApplet.ZERO_INSETS);}});
				add(new JButton(clearAction){{setMargin(ChordHelperApplet.ZERO_INSETS);}});
			}});
			add(new JScrollPane(base64TextArea));
		}});
		setBounds( 300, 250, 660, 300 );
		base64TextArea.setToolTipText("Paste Base64-encoded MIDI sequence here");
		base64TextArea.getDocument().addDocumentListener(this);
		addBase64Action.setEnabled(false);
		clearAction.setEnabled(false);
	}
	@Override
	public void insertUpdate(DocumentEvent e) {
		addBase64Action.setEnabled(true);
		clearAction.setEnabled(true);
	}
	@Override
	public void removeUpdate(DocumentEvent e) {
		if( e.getDocument().getLength() > 0 ) return;
		addBase64Action.setEnabled(false);
		clearAction.setEnabled(false);
	}
	@Override
	public void changedUpdate(DocumentEvent e) { }
	/**
	 * バイナリー形式でMIDIデータを返します。
	 * @return バイナリー形式のMIDIデータ
	 * @throws IllegalArgumentException 入力されているテキストが有効なBase64スキームになっていない場合
	 */
	public byte[] getMIDIData() {
		String body = HEADER_PATTERN.matcher(base64TextArea.getText()).replaceAll("");
		return Base64.getMimeDecoder().decode(body.getBytes());
	}
	/**
	 * バイナリー形式のMIDIデータを設定します。
	 * @param midiData バイナリー形式のMIDIデータ
	 */
	public void setMIDIData(byte[] midiData) { setMIDIData(midiData, null); }
	/**
	 * バイナリー形式のMIDIデータを、ファイル名をつけて設定します。
	 * @param midiData バイナリー形式のMIDIデータ
	 * @param filename ファイル名
	 */
	public void setMIDIData(byte[] midiData, String filename) {
		if( midiData == null || midiData.length == 0 ) return;
		if( filename == null ) filename = "";
		setText(createHeader(filename) + Base64.getMimeEncoder().encodeToString(midiData) + "\n");
		base64TextArea.selectAll();
	}
	/**
	 * Base64形式でMIDIデータを返します。
	 * @return  Base64形式のMIDIデータ
	 */
	public String getBase64Data() { return base64TextArea.getText(); }
	/**
	 * Base64形式のMIDIデータを設定します。
	 * @param base64Data Base64形式のMIDIデータ
	 */
	public void setBase64Data(String base64Data) {
		setText(null);
		base64TextArea.append(base64Data);
	}
	/**
	 * Base64形式のMIDIデータを、ファイル名をつけて設定します。
	 * @param base64Data Base64形式のMIDIデータ
	 * @param filename ファイル名
	 */
	public void setBase64Data(String base64Data, String filename) {
		setText(createHeader(filename));
		base64TextArea.append(base64Data);
	}
	/**
	 * テキスト文字列を設定します。
	 * @param text テキスト文字列
	 */
	public void setText(String text) { base64TextArea.setText(text); }
}
