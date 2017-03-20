package camidion.chordhelper.midieditor;

import javax.swing.JLabel;

import camidion.chordhelper.music.Key;
import camidion.chordhelper.music.Note;

/**
 * 調表示ラベル
 */
public class KeySignatureLabel extends JLabel {
	private Key key;
	public KeySignatureLabel() { clear(); }
	public Key getKey() { return key; }
	public void setKey(Key key) {
		if( (this.key = key) == null ) {
			setText("Key:C");
			setToolTipText("Key: Unknown");
			setEnabled(false);
			return;
		}
		setText( "key:" + key.toString() );
		setToolTipText(
			"Key: " + key.toStringIn(Note.Language.NAME)
			+ " "  + key.toStringIn(Note.Language.IN_JAPANESE)
			+ " (" + key.signatureDescription() + ")"
		);
		setEnabled(true);
	}
	public void clear() { setKey((Key)null); }
}