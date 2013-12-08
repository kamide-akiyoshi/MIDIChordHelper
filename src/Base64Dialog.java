import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.regex.Pattern;

import javax.sound.midi.InvalidMidiDataException;
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

import org.apache.commons.codec.binary.Base64;

/**
 * Base64テキスト入力ダイアログ
 */
public class Base64Dialog extends JDialog {
	private Base64TextArea base64TextArea = new Base64TextArea(8,56);
	private MidiEditor midiEditor;
	/**
	 * Base64デコードアクション
	 */
	public Action addBase64Action = new AbstractAction(
		"Base64 Decode & Add to PlayList",
		new ButtonIcon(ButtonIcon.EJECT_ICON)
	) {
		{
			putValue(
				Action.SHORT_DESCRIPTION,
				"Base64デコードして、プレイリストへ追加"
			);
		}
		@Override
		public void actionPerformed(ActionEvent event) {
			byte[] data = getMIDIData();
			if( data == null || data.length == 0 ) {
				String message = "No data entered - データが入力されていません。";
				JOptionPane.showMessageDialog(
					Base64Dialog.this, message,
					ChordHelperApplet.VersionInfo.NAME,
					JOptionPane.WARNING_MESSAGE
				);
				base64TextArea.requestFocusInWindow();
				return;
			}
			SequenceListTableModel sltm = midiEditor.sequenceListTable.getModel();
			try {
				sltm.addSequence(data, null);
			} catch(IOException | InvalidMidiDataException e) {
				String message = "例外 "+e+" が発生しました。"+e.getMessage();
				e.printStackTrace();
				midiEditor.showWarning(message);
				base64TextArea.requestFocusInWindow();
				return;
			}
			setVisible(false);
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
		private static final Pattern headerLine =
			Pattern.compile( "^.*:.*$", Pattern.MULTILINE );
		public Base64TextArea(int rows, int columns) {
			super(rows,columns);
		}
		public byte[] getBinary() {
			String text = headerLine.matcher(getText()).replaceAll("");
			return Base64.decodeBase64(text.getBytes());
		}
		public void setBinary(byte[] binary_data, String content_type, String filename) {
			if( binary_data != null && binary_data.length > 0 ) {
				String header = "";
				if( content_type != null && filename != null ) {
					header += "Content-Type: " + content_type + "; name=\"" + filename + "\"\n";
					header += "Content-Transfer-Encoding: base64\n";
					header += "\n";
				}
				setText(header + new String(Base64.encodeBase64Chunked(binary_data)) + "\n");
			}
		}
	}
	private static final Insets ZERO_INSETS = new Insets(0,0,0,0);
	/**
	 * Base64テキスト入力ダイアログを構築します。
	 * @param midiEditor 親画面となるMIDIエディタ
	 */
	public Base64Dialog(MidiEditor midiEditor) {
		this.midiEditor = midiEditor;
		setTitle("Base64-encoded MIDI sequence - " + ChordHelperApplet.VersionInfo.NAME);
		try {
			Base64.decodeBase64("".getBytes());
			base64Available = true;
		} catch( NoClassDefFoundError e ) {
			base64Available = false;
		}
		if( base64Available ) {
			add(new JPanel() {{
				setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
				add(new JPanel() {{
					setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
					add(new JLabel("Base64-encoded MIDI sequence:"));
					add(Box.createRigidArea(new Dimension(10, 0)));
					add(new JButton(addBase64Action){{setMargin(ZERO_INSETS);}});
					add(new JButton(clearAction){{setMargin(ZERO_INSETS);}});
				}});
				add(new JScrollPane(base64TextArea));
			}});
		}
		setBounds( 300, 250, 660, 300 );
	}
	private boolean base64Available;
	/**
	 * {@link Base64} が使用できるかどうかを返します。
	 * @return Apache Commons Codec ライブラリが利用できる状態ならtrue
	 */
	public boolean isBase64Available() {
		return base64Available;
	}
	/**
	 * バイナリー形式でMIDIデータを返します。
	 * @return バイナリー形式のMIDIデータ
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
