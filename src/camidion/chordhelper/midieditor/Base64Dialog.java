package camidion.chordhelper.midieditor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.Base64;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import camidion.chordhelper.ButtonIcon;
import camidion.chordhelper.ChordHelperApplet;

/**
 * Base64テキスト入力ダイアログ
 */
public class Base64Dialog extends JDialog {
	private Base64TextArea base64TextArea = new Base64TextArea(8,56);
	private MidiSequenceEditorDialog midiEditor;
	/**
	 * Base64デコードアクション
	 */
	public Action addBase64Action = new AbstractAction(
		"Base64 Decode & Add to PlayList",
		new ButtonIcon(ButtonIcon.EJECT_ICON)
	) {
		{ putValue(Action.SHORT_DESCRIPTION, "Base64デコードして、プレイリストへ追加"); }
		@Override
		public void actionPerformed(ActionEvent event) {
			String message = null;
			try {
				byte[] data = getMIDIData();
				if( data == null || data.length == 0 ) {
					message = "No data on textbox - データが入力されていません。";
				} else {
					midiEditor.sequenceListTable.getModel().addSequence(data, null);
					setVisible(false);
				}
			} catch(Exception e) {
				e.printStackTrace();
				message = "Base64デコードまたはMIDIデータの読み込みに失敗しました。\n"+e;
			}
			if( message == null ) return;
			JOptionPane.showMessageDialog(base64TextArea, (Object)message,
					ChordHelperApplet.VersionInfo.NAME, JOptionPane.WARNING_MESSAGE);
			base64TextArea.requestFocusInWindow();
		}
	};
	/**
	 * Base64テキストクリアアクション
	 */
	public Action clearAction = new AbstractAction("Clear") {
		@Override
		public void actionPerformed(ActionEvent e) {
			base64TextArea.setText(null);
		}
	};
	private static class Base64TextArea extends JTextArea {
		private static final Pattern headerLineFormat =
			Pattern.compile( "^.*:.*$", Pattern.MULTILINE );
		public Base64TextArea(int rows, int columns) {
			super(rows,columns);
		}
		public byte[] getBinary() {
			String text = headerLineFormat.matcher(getText()).replaceAll("");
			return Base64.getMimeDecoder().decode(text.getBytes());
		}
		public void setBinary(byte[] binary_data, String content_type, String filename) {
			if( binary_data != null && binary_data.length > 0 ) {
				String header = "";
				if( content_type != null && filename != null ) {
					header += "Content-Type: " + content_type + "; name=\"" + filename + "\"\n";
					header += "Content-Transfer-Encoding: base64\n";
					header += "\n";
				}
				setText(header + Base64.getMimeEncoder().encodeToString(binary_data) + "\n");
			}
		}
	}
	/**
	 * Base64テキスト入力ダイアログを構築します。
	 * @param midiEditor 親画面となるMIDIエディタ
	 */
	public Base64Dialog(MidiSequenceEditorDialog midiEditor) {
		this.midiEditor = midiEditor;
		setTitle("Base64-encoded MIDI sequence - " + ChordHelperApplet.VersionInfo.NAME);
		add(new JPanel() {{
			setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
			add(new JPanel() {{
				setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
				add(new JLabel("Base64-encoded MIDI sequence:"));
				add(Box.createRigidArea(new Dimension(10, 0)));
				add(new JButton(addBase64Action){{setMargin(ChordHelperApplet.ZERO_INSETS);}});
				add(new JButton(clearAction){{setMargin(ChordHelperApplet.ZERO_INSETS);}});
			}});
			add(new JScrollPane(base64TextArea));
		}});
		setBounds( 300, 250, 660, 300 );
	}
	/**
	 * バイナリー形式でMIDIデータを返します。
	 * @return バイナリー形式のMIDIデータ
	 * @throws IllegalArgumentException 入力されているテキストが有効なBase64スキームになっていない場合
	 */
	public byte[] getMIDIData() {
		return base64TextArea.getBinary();
	}
	/**
	 * バイナリー形式のMIDIデータを設定します。
	 * @param midiData バイナリー形式のMIDIデータ
	 */
	public void setMIDIData( byte[] midiData ) {
		base64TextArea.setBinary(midiData, null, null);
	}
	/**
	 * バイナリー形式のMIDIデータを、ファイル名をつけて設定します。
	 * @param midiData バイナリー形式のMIDIデータ
	 * @param filename ファイル名
	 */
	public void setMIDIData( byte[] midiData, String filename ) {
		base64TextArea.setBinary(midiData, "audio/midi", filename);
		base64TextArea.selectAll();
	}
	/**
	 * Base64形式でMIDIデータを返します。
	 * @return  Base64形式のMIDIデータ
	 */
	public String getBase64Data() {
		return base64TextArea.getText();
	}
	/**
	 * Base64形式のMIDIデータを設定します。
	 * @param base64Data Base64形式のMIDIデータ
	 */
	public void setBase64Data( String base64Data ) {
		base64TextArea.setText(null);
		base64TextArea.append(base64Data);
	}
}
