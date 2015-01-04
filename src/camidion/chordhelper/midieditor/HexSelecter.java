package camidion.chordhelper.midieditor;

import java.awt.FlowLayout;
import java.util.ArrayList;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * 16進数選択 [0x00 0x00 0x00 ... ] v -> Select
 */
public class HexSelecter extends JPanel {
	private JComboBox<String> comboBox = new JComboBox<String>() {{
		setEditable(true);
		setMaximumRowCount(16);
	}};
	private JLabel title;
	public HexSelecter( String title ) {
		if( title != null )
			add( this.title = new JLabel(title) );
		add(comboBox);
		setLayout(new FlowLayout());
	}
	public JComboBox<String> getComboBox() { return comboBox; }
	public void setTitle(String title) { this.title.setText(title); }
	public int getValue() {
		ArrayList<Integer> ia = getIntegerList();
		return ia.size() == 0 ? -1 : ia.get(0);
	}
	public ArrayList<Integer> getIntegerList() {
		String words[];
		String str = (String)(comboBox.getSelectedItem());
		if( str == null )
			words = new String[0];
		else
			words = str.replaceAll( ":.*$", "" ).trim().split(" +");
		int i;
		ArrayList<Integer> ia = new ArrayList<Integer>();
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
			ia.add(i);
		}
		return ia;
	}
	public byte[] getBytes() {
		ArrayList<Integer> ia = getIntegerList();
		byte[] ba = new byte[ia.size()];
		int i = 0;
		for( Integer ib : ia ) {
			ba[i++] = (byte)( ib.intValue() & 0xFF );
		}
		return ba;
	}
	public void setValue( int val ) {
		setValue( (byte)(val & 0xFF) );
	}
	public void setValue( byte val ) {
		int n_item = comboBox.getItemCount();
		String item;
		for( int i=0; i<n_item; i++ ) {
			item = (String)( comboBox.getItemAt(i) );
			if( Integer.decode( item.trim().split(" +")[0] ).byteValue() == val ) {
				comboBox.setSelectedIndex(i);
				return;
			}
		}
		comboBox.setSelectedItem(String.format(" 0x%02X",val));
	}
	public void setValue( byte ba[] ) {
		String str = "";
		for( byte b : ba )
			str += String.format( " 0x%02X", b );
		comboBox.setSelectedItem(str);
	}
	public void clear() {
		comboBox.setSelectedItem("");
	}
}