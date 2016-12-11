package camidion.chordhelper.midieditor;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import camidion.chordhelper.music.Key;
import camidion.chordhelper.music.NoteSymbolLanguage;

/**
 * 調性選択
 */
public class KeySignatureSelecter extends JPanel implements ActionListener {
	public JComboBox<String> keysigCombobox = new JComboBox<String>() {
		{
			String str;
			Key key;
			for( int i = -7 ; i <= 7 ; i++ ) {
				str = (key = new Key(i)).toString();
				if( i != 0 ) {
					str = key.signature() + " : " + str ;
				}
				addItem(str);
			}
			setMaximumRowCount(15);
		}
	};
	JCheckBox minorCheckbox = null;

	public KeySignatureSelecter() { this(true); }
	public KeySignatureSelecter(boolean useMinorCheckbox) {
		add(new JLabel("Key:"));
		add(keysigCombobox);
		if(useMinorCheckbox) {
			add( minorCheckbox = new JCheckBox("minor") );
			minorCheckbox.addActionListener(this);
		}
		keysigCombobox.addActionListener(this);
		clear();
	}
	public void actionPerformed(ActionEvent e) { updateToolTipText(); }
	private void updateToolTipText() {
		Key key = getKey();
		keysigCombobox.setToolTipText(
			"Key: " + key.toStringIn( NoteSymbolLanguage.NAME )
			+ " "  + key.toStringIn( NoteSymbolLanguage.IN_JAPANESE )
			+ " (" + key.signatureDescription() + ")"
		);
	}
	public void clear() { setKey(new Key("C")); }
	public void setKey( Key key ) {
		if( key == null ) { clear(); return; }
		keysigCombobox.setSelectedIndex( key.toCo5() + 7 );
		if( minorCheckbox == null ) return;
		minorCheckbox.setSelected(key.majorMinor() == Key.MajorMinor.MINOR);
	}
	public Key getKey() {
		Key.MajorMinor majorMinor = (
			minorCheckbox == null ? Key.MajorMinor.MAJOR_OR_MINOR :
			isMinor() ? Key.MajorMinor.MINOR :
			Key.MajorMinor.MAJOR
		);
		return new Key(getKeyCo5(),majorMinor);
	}
	public int getKeyCo5() {
		return keysigCombobox.getSelectedIndex() - 7;
	}
	public boolean isMinor() {
		return minorCheckbox != null && minorCheckbox.isSelected();
	}
}