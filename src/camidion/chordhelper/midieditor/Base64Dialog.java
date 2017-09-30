package camidion.chordhelper.midieditor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.regex.Matcher;
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
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import camidion.chordhelper.ButtonIcon;
import camidion.chordhelper.ChordHelperApplet;

/**
 * Base64テキスト入力ダイアログ
 */
public class Base64Dialog extends JDialog {
	private JTextArea base64TextArea = new JTextArea(8,56);
	private PlaylistTable playlistTable;
	private void decodeError(String message) {
		JOptionPane.showMessageDialog(base64TextArea, (Object)message,
				ChordHelperApplet.VersionInfo.NAME, JOptionPane.WARNING_MESSAGE);
		base64TextArea.requestFocusInWindow();
	}
	private static String createHeaderOfFilename(String filename) {
		String header = "Content-Type: audio/midi; name=\"";
		if( filename != null ) header += filename;
		header += "\"\nContent-Transfer-Encoding: base64\n\n";
		return header;
	}
	private static String createBase64TextWithHeader(SequenceTrackListTableModel sequenceModel) throws IOException {
		if( sequenceModel == null ) return null;
		String text = createHeaderOfFilename(sequenceModel.getFilename());
		byte[] midiData = sequenceModel.getMIDIdata();
		if( midiData != null && midiData.length > 0 )
			text += Base64.getMimeEncoder().encodeToString(midiData);
		text += "\n";
		return text;
	}
	private static String bodyOf(String base64TextWithHeader) {
		// bodyには":"が含まれないのでヘッダと混同する心配なし
		return Pattern.compile("^.*:.*$", Pattern.MULTILINE).matcher(base64TextWithHeader).replaceAll("");
	}
	private static String filenameOf(String base64TextWithHeader) {
		Matcher m = Pattern.compile("(?i)^Content-Type:.*name=\"(.*)\"$", Pattern.MULTILINE).matcher(base64TextWithHeader);
		return m.find() ? m.group(1) : "";
	}
	/**
	 * 入力されたBase64テキストをデコードし、MIDIシーケンスとしてプレイリストに追加します。
	 * @return プレイリストに追加されたMIDIシーケンスのインデックス（先頭が0）、追加に失敗した場合は -1
	 */
	public int addToPlaylist() {
		String base64Text = base64TextArea.getText();
		byte[] decodedData;
		try {
			decodedData = Base64.getMimeDecoder().decode(bodyOf(base64Text).getBytes());
		} catch(Exception ex) {
			// 不正なBase64テキストが入力された場合
			decodeError("Base64デコードに失敗しました。\n"+ex);
			return -1;
		}
		Sequence sequence;
		try (InputStream in = new ByteArrayInputStream(decodedData)) {
			sequence = MidiSystem.getSequence(in);
		} catch( IOException|InvalidMidiDataException ex ) {
			// MIDI以外のデータをエンコードしたBase64テキストが入力された場合
			decodeError("Base64デコードした結果をMIDIシーケンスとして読み込めませんでした。\n"+ex);
			return -1;
		}
		int newIndex;
		try {
			newIndex = playlistTable.getModel().add(sequence, filenameOf(base64Text));
		} catch(Exception ex) {
			// 何らかの理由でプレイリストへの追加ができなかった場合
			decodeError("Base64デコードしたMIDIシーケンスをプレイリストに追加できませんでした。\n"+ex);
			return -1;
		}
		ListSelectionModel sm = playlistTable.getSelectionModel();
		if( sm != null ) sm.setSelectionInterval(newIndex, newIndex);
		return newIndex;
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
		public void actionPerformed(ActionEvent e) { base64TextArea.setText(null); }
	};
	private void setActionEnabled(boolean b) {
		addBase64Action.setEnabled(b);
		clearAction.setEnabled(b);
	}
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
		base64TextArea.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				setActionEnabled(true);
			}
			@Override
			public void removeUpdate(DocumentEvent e) {
				if( e.getDocument().getLength() > 0 ) return;
				setActionEnabled(false);
			}
			@Override
			public void changedUpdate(DocumentEvent e) { }
		});
		setActionEnabled(false);
	}
	/**
	 * MIDIシーケンスモデルを設定します。
	 * @param sequenceModel MIDIシーケンスモデル
	 */
	public void setSequenceModel(SequenceTrackListTableModel sequenceModel) {
		String text;
		try {
			text = createBase64TextWithHeader(sequenceModel);
		} catch (IOException ioex) {
			text = "File[" + sequenceModel.getFilename() + "]:" + ioex;
		}
		base64TextArea.setText(text);
		base64TextArea.selectAll();
	}
	/**
	 * Base64形式でテキスト化されたMIDIデータを返します。
	 * @return  Base64形式のMIDIデータ
	 */
	public String getBase64TextData() { return base64TextArea.getText(); }
	/**
	 * Base64形式でテキスト化されたMIDIデータを、ヘッダつきで設定します。
	 * @param base64TextData Base64形式のMIDIデータ
	 * @param filename ヘッダに含めるファイル名（nullを指定すると""として設定される）
	 */
	public void setBase64TextData(String base64TextData, String filename) {
		base64TextArea.setText(createHeaderOfFilename(filename));
		base64TextArea.append(base64TextData);
	}
}
