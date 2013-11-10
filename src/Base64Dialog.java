import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.regex.Pattern;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.apache.commons.codec.binary.Base64;

/**
 * Base64テキスト入力ダイアログ
 */
public class Base64Dialog extends JDialog implements ActionListener {
	private static final Insets ZERO_INSETS = new Insets(0,0,0,0);
	private Base64TextArea base64TextArea = null;
	private JButton addBase64Button;
	private JButton clearButton;
	private JPanel base64Panel;
	private MidiEditor midiEditor;
	private boolean base64Available;
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
	/**
	 * Base64テキスト入力ダイアログを構築します。
	 * @param midiEditor 親画面となるMIDIエディタ
	 */
	public Base64Dialog(MidiEditor midiEditor) {
		this.midiEditor = midiEditor;
		setTitle("Base64-encoded MIDI sequence - " + ChordHelperApplet.VersionInfo.NAME);
		try {
			Base64.decodeBase64( "".getBytes() );
			base64Available = true;
		} catch( NoClassDefFoundError e ) {
			base64Available = false;
		}
		if( base64Available ) {
			base64TextArea = new Base64TextArea(8,56);
			JScrollPane scrollable_media_text = new JScrollPane(base64TextArea);
			addBase64Button = new JButton( "Base64 Decode & Add to PlayList", new ButtonIcon(ButtonIcon.EJECT_ICON) );
			addBase64Button.setMargin(ZERO_INSETS);
			addBase64Button.addActionListener(this);
			addBase64Button.setToolTipText("Base64デコードして、プレイリストへ追加");
			clearButton = new JButton( "Clear" );
			clearButton.setMargin(ZERO_INSETS);
			clearButton.addActionListener(this);

			base64Panel = new JPanel();
			base64Panel.setLayout( new BoxLayout( base64Panel, BoxLayout.PAGE_AXIS ) );
			JPanel base64_button_panel = new JPanel();
			base64_button_panel.setLayout( new BoxLayout( base64_button_panel, BoxLayout.LINE_AXIS ) );
			base64_button_panel.add( new JLabel("Base64-encoded MIDI sequence:") );
			base64_button_panel.add( Box.createRigidArea(new Dimension(10, 0)) );
			base64_button_panel.add( addBase64Button );
			base64_button_panel.add( clearButton );
			base64Panel.add( base64_button_panel );
			base64Panel.add( scrollable_media_text );
			add(base64Panel);
		}
		// setLocationRelativeTo(applet);
		setBounds( 300, 250, 660, 300 );
	}
	@Override
	public void actionPerformed(ActionEvent event) {
		Object obj = event.getSource();
		if( obj == addBase64Button ) {
			int last_index = midiEditor.addSequenceFromMidiData( getMIDIData(), null );
			if( last_index < 0 ) {
				base64TextArea.requestFocusInWindow();
				last_index = midiEditor.sequenceListTableModel.getRowCount() - 1;
			}
			midiEditor.seqSelectionModel.setSelectionInterval( last_index, last_index );
			setVisible(false);
		}
		else if( obj == clearButton ) {
			base64TextArea.setText(null);
		}
	}
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
