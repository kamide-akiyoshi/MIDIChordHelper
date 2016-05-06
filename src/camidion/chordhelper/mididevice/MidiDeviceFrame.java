package camidion.chordhelper.mididevice;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.BoxLayout;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.Timer;

/**
 * MIDIデバイスフレームビュー
 */
public class MidiDeviceFrame extends JInternalFrame {

	private MidiConnecterListView listView;
	private JScrollPane scrollPane;
	private Timer timer;

	/**
	 * このデバイスフレームに貼り付けられた仮想MIDI端子リストビューを取得します。
	 * @return 仮想MIDI端子リストビュー
	 */
	public MidiConnecterListView getMidiConnecterListView() { return listView; }

	/**
	 * このデバイスフレームのタイムスタンプ表示更新用タイマーを取得します。
	 * @return タイムスタンプ表示更新用タイマー
	 */
	public Timer getTimer() { return timer; }

	/**
	 * MIDIデバイスのモデルからフレームビューを構築します。
	 * @param model MIDIデバイスのTransmitter/Receiverリストモデル
	 * @param cablePane MIDIケーブル描画面
	 */
	public MidiDeviceFrame(MidiConnecterListModel model, MidiCablePane cablePane) {
		super( null, true, true, false, false );
		listView = new MidiConnecterListView(model, cablePane);
		addInternalFrameListener(cablePane.midiDeviceFrameListener);
		addComponentListener(cablePane.midiDeviceFrameComponentListener);
		setTitle("[" + model.getMidiDeviceInOutType().getShortName() + "] " + model);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		add(new JLabel("--:--") {{
			timer = new Timer(50, new ActionListener() {
				private long sec = -2;
				private MidiDevice dev = listView.getModel().getMidiDevice();
				@Override
				public void actionPerformed(ActionEvent e) {
					long usec = dev.getMicrosecondPosition();
					long sec = (usec == -1 ? -1 : usec/1000000);
					if( sec == this.sec ) return;
					this.sec = sec;
					setText(sec == -1?"--:--":String.format("%02d:%02d",sec/60,sec%60));
				}
			});
		}});
		add(scrollPane = new JScrollPane(listView));
		setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
	}
	/**
	 * 引数で指定された{@link Transmitter}または{@link Receiver}のセル範囲を示す、
	 * デスクトップの座標系内の境界の矩形を返します。対応するセルがない場合はnullを返します。
	 * @param transceiver {@link Transmitter}または{@link Receiver}
	 * @return セル範囲を示す境界の矩形、またはnull
	 */
	public Rectangle getBoundsOf(AutoCloseable transceiver) {
		Rectangle rect = listView.getCellBounds(transceiver);
		if( rect != null ) rect.translate(
			getRootPane().getX() + getContentPane().getX() + scrollPane.getX() + getX(),
			getRootPane().getY() + getContentPane().getY() + scrollPane.getY() + getY()
		);
		return rect;
	}
}
