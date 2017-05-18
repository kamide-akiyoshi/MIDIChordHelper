package camidion.chordhelper.midieditor;

import java.nio.charset.Charset;

import javax.swing.JComboBox;

public class CharsetComboBox extends JComboBox<Charset> {
	{
		Charset.availableCharsets().values().stream().forEach(v->addItem(v));
		setSelectedItem(Charset.defaultCharset());
	}
	public Charset getSelectedCharset() {
		return (Charset)getSelectedItem();
	}
}
