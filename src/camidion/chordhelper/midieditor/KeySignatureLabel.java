package camidion.chordhelper.midieditor;

import javax.swing.JLabel;

import camidion.chordhelper.music.Key;
import camidion.chordhelper.music.SymbolLanguage;

/**
 * 調表示ラベル
 */
public class KeySignatureLabel extends JLabel {
	private Key key;
	public KeySignatureLabel() { clear(); }
	public Key getKey() { return key; }
	public void setKeySignature( Key key ) {
		this.key = key;
		if( key == null ) {
			setText("Key:C");
			setToolTipText("Key: Unknown");
			setEnabled(false);
			return;
		}
		setText( "key:" + key.toString() );
		setToolTipText(
			"Key: " + key.toStringIn(SymbolLanguage.NAME)
			+ " "  + key.toStringIn(SymbolLanguage.IN_JAPANESE)
			+ " (" + key.signatureDescription() + ")"
		);
		setEnabled(true);
	}
	public void clear() { setKeySignature( (Key)null ); }
}