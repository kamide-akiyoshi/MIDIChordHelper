package camidion.chordhelper.midieditor;

import javax.swing.JCheckBox;

import camidion.chordhelper.music.Key;

public class MinorCheckBox extends JCheckBox {
	public MinorCheckBox() { super("minor"); }
	public void setMajorMinor(Key.MajorMinor majorMinor) {
		if( majorMinor == Key.MajorMinor.MAJOR_OR_MINOR ) return;
		setSelected( majorMinor == Key.MajorMinor.MINOR );
	}
	public Key.MajorMinor getMajorMinor() {
		return isSelected() ? Key.MajorMinor.MINOR : Key.MajorMinor.MAJOR;
	}
}
