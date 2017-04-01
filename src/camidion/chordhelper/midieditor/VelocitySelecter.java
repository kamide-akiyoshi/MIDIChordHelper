package camidion.chordhelper.midieditor;

import java.awt.Color;
import java.awt.Label;

import javax.swing.BoundedRangeModel;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

/**
 * ベロシティ選択ビュー
 */
public class VelocitySelecter extends JPanel {
	private static final String	LABEL_PREFIX = "Velocity=";
	private JSlider slider;
	public VelocitySelecter(BoundedRangeModel model) {
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		JLabel label = new JLabel(LABEL_PREFIX + model.getValue(), Label.RIGHT) {{
			setToolTipText("Velocity");
		}};
		add(label);
		add(slider = new JSlider(model) {{ setToolTipText("Velocity"); }});
		slider.addChangeListener(e->label.setText(LABEL_PREFIX + getValue()));
	}
	@Override
	public void setBackground(Color c) {
		super.setBackground(c);
		// このクラスが構築される前にスーパークラスの
		// Look & Feel からここが呼ばれることがあるため
		// null チェックが必要
		if( slider != null ) slider.setBackground(c);
	}
	public int getValue() { return slider.getValue(); }
	public void setValue(int velocity) { slider.setValue(velocity); }
}