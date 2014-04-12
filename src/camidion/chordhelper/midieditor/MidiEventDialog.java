package camidion.chordhelper.midieditor;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;

public class MidiEventDialog extends JDialog {
	/**
	 * tick位置入力フォーム
	 */
	public static class TickPositionInputForm extends JPanel {
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
	/**
	 * tick位置入力フォーム
	 */
	public MidiEventDialog.TickPositionInputForm tickPositionInputForm;
	/**
	 * MIDIメッセージ入力フォーム
	 */
	public MidiMessageForm midiMessageForm;
	/**
	 * キャンセルボタン
	 */
	private JButton cancelButton;
	/**
	 * OKボタン（アクションによってラベルがOK以外に変わることがある）
	 */
	private JButton okButton;
	/**
	 * MIDIイベントダイアログの構築
	 */
	public MidiEventDialog() {
		setLayout(new FlowLayout());
		add(tickPositionInputForm = new TickPositionInputForm());
		add(midiMessageForm = new MidiMessageForm());
		add(new JPanel(){{
			add(okButton = new JButton("OK"));
			add(cancelButton = new JButton("Cancel"));
		}});
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setVisible(false);
			}
		});
	}
	public void openForm(
		String title, Action okAction, int midiChannel,
		boolean useTick, boolean useMessage
	) {
		setTitle(title);
		okButton.setAction(okAction);
		if( useMessage && midiChannel >= 0 ) {
			midiMessageForm.channelText.setSelectedChannel(midiChannel);
		}
		tickPositionInputForm.setVisible(useTick);
		midiMessageForm.setVisible(useMessage);
		midiMessageForm.setDurationVisible(useMessage && useTick);
		int width = useMessage ? 630 : 520;
		int height = useMessage ? (useTick ? 370 : 300) : 150;
		setBounds( 200, 300, width, height );
		setVisible(true);
	}
	public void openTickForm(String title, Action okAction) {
		openForm(title, okAction, -1, true, false);
	}
	public void openMessageForm(String title, Action okAction, int midiChannel) {
		openForm(title, okAction, midiChannel, false, true);
	}
	public void openEventForm(String title, Action okAction, int midiChannel) {
		openForm(title, okAction, midiChannel, true, true);
	}
	public void openEventForm(String title, Action okAction) {
		openEventForm(title, okAction, -1);
	}
}