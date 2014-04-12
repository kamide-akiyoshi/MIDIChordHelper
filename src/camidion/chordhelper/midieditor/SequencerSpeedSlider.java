package camidion.chordhelper.midieditor;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoundedRangeModel;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

/**
 * シーケンサーの再生スピード調整スライダビュー
 */
public class SequencerSpeedSlider extends JPanel {
	private static final String items[] = {
		"x 1.0",
		"x 1.5",
		"x 2",
		"x 4",
		"x 8",
		"x 16",
	};
	private JLabel titleLabel;
	private JSlider slider;
	public SequencerSpeedSlider(BoundedRangeModel model) {
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(titleLabel = new JLabel("Speed:"));
		add(slider = new JSlider(model){{
			setPaintTicks(true);
			setMajorTickSpacing(12);
			setMinorTickSpacing(1);
			setVisible(false);
		}});
		add(new JComboBox<String>(items) {{
			addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					int index = getSelectedIndex();
					BoundedRangeModel model = slider.getModel();
					if( index == 0 ) {
						model.setValue(0);
						slider.setVisible(false);
						titleLabel.setVisible(true);
					}
					else {
						int maxValue = ( index == 1 ? 7 : (index-1)*12 );
						model.setMinimum(-maxValue);
						model.setMaximum(maxValue);
						slider.setMajorTickSpacing( index == 1 ? 7 : 12 );
						slider.setMinorTickSpacing( index > 3 ? 12 : 1 );
						slider.setVisible(true);
						titleLabel.setVisible(false);
					}
				}
			});
		}});
	}
}