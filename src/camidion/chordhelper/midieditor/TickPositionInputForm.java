package camidion.chordhelper.midieditor;

import java.awt.GridLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;

/**
 * tick位置入力フォーム
 */
public class TickPositionInputForm extends JPanel {
	private JSpinner tickSpinner = new JSpinner();
	private JSpinner measureSpinner = new JSpinner();
	private JSpinner beatSpinner = new JSpinner();
	private JSpinner extraTickSpinner = new JSpinner();
	public TickPositionInputForm() {
		setLayout(new GridLayout(2,4));
		add( new JLabel() );
		add( new JLabel() );
		add( new JLabel("Measure:") );
		add( new JLabel("Beat:") );
		add( new JLabel("ExTick:") );
		add( new JLabel("Tick position : ",JLabel.RIGHT) );
		add( tickSpinner );
		add( measureSpinner );
		add( beatSpinner );
		add( extraTickSpinner );
	}
	public void setModel(TickPositionModel model) {
		tickSpinner.setModel(model.tickModel);
		measureSpinner.setModel(model.measureModel);
		beatSpinner.setModel(model.beatModel);
		extraTickSpinner.setModel(model.extraTickModel);
	}
}
