package camidion.chordhelper.mididevice;

import java.awt.Rectangle;
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
	 */
	public MidiDeviceFrame(MidiConnecterListModel model) {
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
		listView = new MidiConnecterListView(model);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addInternalFrameListener(
			new InternalFrameAdapter() {
				public void internalFrameOpened(InternalFrameEvent e) {
					if( ! listView.getModel().getMidiDevice().isOpen() )
						setVisible(false);
				}
				public void internalFrameClosing(InternalFrameEvent e) {
					MidiConnecterListModel m = listView.getModel();
					m.closeDevice();
					if( ! m.getMidiDevice().isOpen() )
						setVisible(false);
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
	/**
	 * 指定されたインデックスが示す仮想MIDI端子リストの要素のセル範囲を返します。
	 *
	 * @param index リスト要素のインデックス
	 * @return セル範囲の矩形
	 */
	public Rectangle getListCellBounds(int index) {
		Rectangle rect = listView.getCellBounds(index,index);
		if( rect == null )
			return null;
		rect.translate(
			getRootPane().getX() + getContentPane().getX(),
			getRootPane().getY() + getContentPane().getY()
		);
		return rect;
	}
	/**
	 * 仮想MIDI端子リストの指定された要素のセル範囲を返します。
	 *
	 * @param transciver 要素となるMIDI端子（Transmitter または Receiver）
	 * @return セル範囲の矩形
	 */
	public Rectangle getListCellBounds(AutoCloseable transciver) {
		return getListCellBounds(listView.getModel().indexOf(transciver));
	}
}