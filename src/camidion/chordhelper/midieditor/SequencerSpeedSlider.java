package camidion.chordhelper.midieditor;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.swing.AbstractSpinnerModel;
import javax.swing.BoundedRangeModel;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * シーケンサーの再生スピード調整スライダビュー
 */
public class SequencerSpeedSlider extends JPanel {
	public static float tempoFactorOf(int val) {
		return (float) Math.pow( 2, ((double)val)/12.0 );
	}
	private static final List<Hashtable<Integer,JComponent>> labeltables = new ArrayList<Hashtable<Integer,JComponent>>() {{
		for( int i = 0; i < 5; i++ ) {
			Hashtable<Integer,JComponent> e = new Hashtable<>();
			add(e);
			e.put(-i * 12, new JLabel( "x" + Double.toString(Math.pow( 2, (double)(-i) )) ));
			e.put(0, new JLabel( "x1.0" ));
			e.put(i * 12, new JLabel( "x" + Double.toString(Math.pow( 2, (double)i )) ));
		}
	}};
	private JLabel titleLabel;
	private JSlider slider;
	public SequencerSpeedSlider(BoundedRangeModel model) {
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(titleLabel = new JLabel("Speed: "));
		add(slider = new JSlider(model){{
			setPaintTicks(true);
			setMajorTickSpacing(12);
			setMinorTickSpacing(1);
			setPaintLabels(true);
			setVisible(false);
		}});
		add(new JLabel("x"));
		add(new JSpinner(new AbstractSpinnerModel() {
			private int index = 0;
			@Override
			public Object getValue() { return Math.pow( 2, (double)index ); }
			@Override
			public void setValue(Object value) {
				index =  (int) Math.round( Math.log((Double)value) / Math.log(2) );
				fireStateChanged();
			}
			@Override
			public Object getNextValue() {
				return index >= 4 ? null : Math.pow( 2, (double)(++index) );
			}
			@Override
			public Object getPreviousValue() {
				return index <= 0 ? null : Math.pow( 2, (double)(--index) );
			}
		}) {{
			addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					int index = (int) Math.round( Math.log((Double)getValue()) / Math.log(2) );
					BoundedRangeModel model = slider.getModel();
					if( index == 0 ) {
						model.setValue(0);
						slider.setVisible(false);
						titleLabel.setVisible(true);
						return;
					}
					int maxValue = index * 12;
					model.setMinimum(-maxValue);
					model.setMaximum(maxValue);
					slider.setMajorTickSpacing(12);
					slider.setMinorTickSpacing(index > 2 ? 12 : 1);
					slider.setLabelTable(labeltables.get(index));
					slider.setVisible(true);
					titleLabel.setVisible(false);
				}
			});
		}});
	}
}