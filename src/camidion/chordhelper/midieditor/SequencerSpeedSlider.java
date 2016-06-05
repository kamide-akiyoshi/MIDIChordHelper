package camidion.chordhelper.midieditor;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import javax.swing.BoundedRangeModel;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.EtchedBorder;

import camidion.chordhelper.ChordHelperApplet;

/**
 * シーケンサーの再生スピード調整スライダビュー
 */
public class SequencerSpeedSlider extends JPanel {
	private static int RESOLUTION = 1200;
	private static final List<String> maxFactors = Arrays.asList(
			"x1", "x1.2", "x1.25", "x1.5", "x2", "x3", "x4", "x5", "x6",
			"x8", "x12", "x16", "x24", "x32");
	public static float tempoFactorOf(int val) {
		return (float) Math.pow( 2, ((double)val)/RESOLUTION );
	}
	private static final List<Hashtable<Integer,JComponent>>
		tickLabeltables = new ArrayList<Hashtable<Integer,JComponent>>()
	{
		{
			NumberFormat formetter =  NumberFormat.getInstance();
			for( String maxFactorString : maxFactors ) {
				Hashtable<Integer,JComponent> e = new Hashtable<>();
				Double maxFactor = Double.parseDouble(maxFactorString.substring(1));
				add(e);
				int maxValue = (int) Math.round(Math.log(maxFactor) / Math.log(2) * RESOLUTION);
				if( maxFactorString.equals("x1") ) {
					e.put(0, new JLabel("Play speed : x1 (Original)"));
				} else {
					e.put(-maxValue, new JLabel("x" + formetter.format(1/maxFactor)));
					e.put(0, new JLabel("x1"));
					e.put(maxValue, new JLabel(maxFactorString));
				}
			}
		}
	};
	private JSlider slider;
	private void changeSliderScale(String maxFactorString) {
		int index = maxFactors.indexOf(maxFactorString);
		slider.setLabelTable(tickLabeltables.get(index));
		BoundedRangeModel sliderModel = slider.getModel();
		Double maxFactor = Double.parseDouble(maxFactorString.substring(1));
		int maxValue = (int) Math.round(Math.log(maxFactor) / Math.log(2) * RESOLUTION);
		slider.setMajorTickSpacing((int) Math.round((double)maxValue / 2.0));
		if( index > 0 ) {
			sliderModel.setMinimum(-maxValue);
			sliderModel.setMaximum(maxValue);
			slider.setEnabled(true);
		} else {
			sliderModel.setMinimum(-1);
			sliderModel.setValue(0);
			sliderModel.setMaximum(1);
			slider.setEnabled(false);
		}
	}
	public SequencerSpeedSlider(BoundedRangeModel model) {
		setBorder(new EtchedBorder());
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(slider = new JSlider(model){
			{
				setPaintTicks(true);
				setMajorTickSpacing(RESOLUTION/2);
				setPaintLabels(true);
				setLabelTable(tickLabeltables.get(0));
				setEnabled(false);
			}
		});
		add(new JPanel() {
			private int index = 0;
			private JButton push, pull;
			{
				setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
				add(pull = new JButton("=>|") {
					{
						setSize(new Dimension(20,20));
						setMargin(ChordHelperApplet.ZERO_INSETS);
						addActionListener(new ActionListener() {
							@Override
							public void actionPerformed(ActionEvent e) {
								int max = maxFactors.size() - 1;
								if( index >= max ) return;
								changeSliderScale(maxFactors.get(++index));
								if( index >= max ) {
									setEnabled(false);
									push.requestFocusInWindow();
									return;
								}
								push.setEnabled(true);
							}
						});
					}
				});
				add(push = new JButton("<=|") {
					{
						setSize(new Dimension(20,20));
						setMargin(ChordHelperApplet.ZERO_INSETS);
						setEnabled(false);
						addActionListener(new ActionListener() {
							@Override
							public void actionPerformed(ActionEvent e) {
								if( index <= 0 ) return;
								changeSliderScale(maxFactors.get(--index));
								if( index <= 0 ) {
									setEnabled(false);
									pull.requestFocusInWindow();
									return;
								}
								pull.setEnabled(true);
							}
						});
					}
				});
			}
		});
	}
}
