package camidion.chordhelper.mididevice;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.sound.midi.MidiDevice;
import javax.swing.BoxLayout;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;

/**
 * MIDIデバイスフレームビュー
 */
public class MidiDeviceFrame extends JInternalFrame {
	/**
	 * デバイスの仮想MIDI端子リストビュー
	 */
	MidiConnecterListView listView;
	/**
	 * デバイスのタイムスタンプを更新するタイマー
	 */
	Timer timer;
	/**
	 * MIDIデバイスのモデルからフレームビューを構築します。
	 * @param model MIDIデバイスのTransmitter/Receiverリストモデル
	 * @param cablePane MIDIケーブル描画面
	 */
	public MidiDeviceFrame(MidiConnecterListModel model, MidiCablePane cablePane) {
		super( null, true, true, false, false );
		//
		// タイトルの設定
		String title = model.toString();
		if( model.txSupported() ) {
			title = (model.rxSupported()?"[I/O] ":"[IN] ")+title;
		}
		else {
			title = (model.rxSupported()?"[OUT] ":"[No I/O] ")+title;
		}
		setTitle(title);
		listView = new MidiConnecterListView(model, cablePane);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addInternalFrameListener(
			new InternalFrameAdapter() {
				public void internalFrameClosing(InternalFrameEvent e) {
					MidiConnecterListModel m = listView.getModel();
					m.closeDevice();
					setVisible(m.getMidiDevice().isOpen());
				}
			}
		);
		setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
		add(new JScrollPane(listView));
		add(new JPanel() {{
			add(new JLabel() {{
				timer = new Timer(50, new ActionListener() {
					private long sec = -2;
					private MidiDevice dev = listView.getModel().getMidiDevice();
					@Override
					public void actionPerformed(ActionEvent e) {
						long usec = dev.getMicrosecondPosition();
						long sec = (usec == -1 ? -1 : usec/1000000);
						if( sec == this.sec ) return;
						String text;
						if( (this.sec = sec) == -1 )
							text = "No TimeStamp";
						else
							text = String.format("TimeStamp: %02d:%02d", sec/60, sec%60);
						setText(text);
					}
				});
			}});
		}});
	}
}
