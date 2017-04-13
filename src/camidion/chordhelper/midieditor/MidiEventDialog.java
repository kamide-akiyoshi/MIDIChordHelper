package camidion.chordhelper.midieditor;

import java.awt.FlowLayout;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;

public class MidiEventDialog extends JDialog {
	/**
	 * tick位置入力フォーム
	 */
	public TickPositionInputForm tickPositionInputForm;
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
		cancelButton.addActionListener(e->setVisible(false));
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