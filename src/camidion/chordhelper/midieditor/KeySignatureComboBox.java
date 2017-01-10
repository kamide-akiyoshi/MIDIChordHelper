package camidion.chordhelper.midieditor;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComboBox;

import camidion.chordhelper.music.Key;
import camidion.chordhelper.music.Note;

public class KeySignatureComboBox extends JComboBox<Key> implements ActionListener {
	{
		for(int co5 = -Key.MAX_SHARPS_OR_FLATS ; co5 <= Key.MAX_SHARPS_OR_FLATS ; co5++)
			addItem(new Key(co5));
		setMaximumRowCount(getItemCount());
		addActionListener(this);
	}
	@Override
	public void actionPerformed(ActionEvent e) {
		String text = "Key: ";
		Key k = (Key) getSelectedItem();
		text += k.toStringIn(Note.Language.NAME);
		text += " "  + k.toStringIn(Note.Language.IN_JAPANESE);
		text += " (" + k.signatureDescription() + ")";
		setToolTipText(text);
	}
	@Override
	public void setSelectedItem(Object anObject) {
		if( anObject instanceof Key ) {
			Key k = (Key) anObject;
			if( k.majorMinor() != Key.MajorMinor.MAJOR_OR_MINOR )
				anObject = new Key(k.toCo5());
		}
		super.setSelectedItem(anObject);
	}
}
