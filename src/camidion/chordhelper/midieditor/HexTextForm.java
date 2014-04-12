package camidion.chordhelper.midieditor;

import java.awt.FlowLayout;
import java.util.ArrayList;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * 16進数テキスト入力フォーム [0x00 0x00 0x00 ... ]
 */
public class HexTextForm extends JPanel {
	public JTextArea textArea;
	public JLabel titleLabel;
	public HexTextForm(String title) {
		this(title,1,3);
	}
	public HexTextForm(String title, int rows, int columns) {
		if( title != null )
			add(titleLabel = new JLabel(title));
		textArea = new JTextArea(rows, columns) {{
			setLineWrap(true);
		}};
		add(new JScrollPane(textArea));
		setLayout(new FlowLayout());
	}
	public String getString() {
		return textArea.getText();
	}
	public byte[] getBytes() {
		String words[] = getString().trim().split(" +");
		ArrayList<Integer> tmp_ba = new ArrayList<Integer>();
		int i;
		for( String w : words ) {
			if( w.length() == 0 ) continue;
			try {
				i = Integer.decode(w).intValue();
			} catch( NumberFormatException e ) {
				JOptionPane.showMessageDialog(
						this,
						w + " : is not a number",
						"MIDI Chord Helper",
						JOptionPane.ERROR_MESSAGE
						);
				return null;
			}
			tmp_ba.add(i);
		}
		byte[] ba = new byte[tmp_ba.size()];
		i = 0;
		for( Integer b : tmp_ba ) {
			ba[i++] = (byte)( b.intValue() & 0xFF );
		}
		return ba;
	}
	public void setTitle( String str ) {
		titleLabel.setText( str );
	}
	public void setString( String str ) {
		textArea.setText( str );
	}
	public void setValue( int val ) {
		textArea.setText( String.format( " 0x%02X", val ) );
	}
	public void setValue( byte val ) {
		textArea.setText( String.format( " 0x%02X", val ) );
	}
	public void setValue( byte ba[] ) {
		String str = "";
		for( byte b : ba ) {
			str += String.format( " 0x%02X", b );
		}
		textArea.setText(str);
	}
	public void clear() { textArea.setText(""); }
}