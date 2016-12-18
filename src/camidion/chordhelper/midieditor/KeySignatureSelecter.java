package camidion.chordhelper.midieditor;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import camidion.chordhelper.music.Key;
import camidion.chordhelper.music.NoteSymbol;

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
				if( i != 0 ) str = key.signature() + " : " + str ;
				addItem(str);
			}
			setMaximumRowCount(15);
		}
	};
	JCheckBox minorCheckbox = null;

	public KeySignatureSelecter() { this(null); }
	public KeySignatureSelecter(Key key) {
		add(new JLabel("Key:"));
		add(keysigCombobox);
		if(key != null && key.majorMinor() != Key.MajorMinor.MAJOR_OR_MINOR) {
			add( minorCheckbox = new JCheckBox("minor") );
			minorCheckbox.addActionListener(this);
		}
		keysigCombobox.addActionListener(this);
		setSelectedKey(key);
	}

	public void actionPerformed(ActionEvent e) { updateToolTipText(); }
	private void updateToolTipText() {
		Key key = getSelectedKey();
		keysigCombobox.setToolTipText(
			"Key: " + key.toStringIn( NoteSymbol.Language.NAME )
			+ " "  + key.toStringIn( NoteSymbol.Language.IN_JAPANESE )
			+ " (" + key.signatureDescription() + ")"
		);
	}

	public void setSelectedKey(Key key) {
		if( key == null ) key = new Key("C");
		keysigCombobox.setSelectedIndex(key.toCo5() + 7);
		setMajorMinor(key.majorMinor());
	}
	public Key getSelectedKey() {
		return new Key(keysigCombobox.getSelectedIndex() - 7, getMajorMinor());
	}

	private void setMajorMinor(Key.MajorMinor majorMinor) {
		if( minorCheckbox == null || majorMinor == Key.MajorMinor.MAJOR_OR_MINOR ) return;
		minorCheckbox.setSelected(majorMinor == Key.MajorMinor.MINOR);
	}
	private Key.MajorMinor getMajorMinor() {
		return minorCheckbox == null ? Key.MajorMinor.MAJOR_OR_MINOR :
			minorCheckbox.isSelected() ? Key.MajorMinor.MINOR : Key.MajorMinor.MAJOR;
	}
}