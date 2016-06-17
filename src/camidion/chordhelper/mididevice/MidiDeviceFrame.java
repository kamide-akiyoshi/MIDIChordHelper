package camidion.chordhelper.mididevice;

import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;

/**
 * MIDIデバイスフレームビュー
 */
public class MidiDeviceFrame extends JInternalFrame {
	private MidiDeviceModel deviceModel;
	private MidiTransmitterListView transmitterListView;
	private MidiReceiverListView receiverListView;
	private JScrollPane scrollPane;
	private JPanel trxPanel, txPanel, rxPanel;
	private Timer timer;
	/**
	 * このデバイスフレームに表示内容を提供しているMIDIデバイスモデルを取得します。
	 * @return MIDIデバイスモデル
	 */
	public MidiDeviceModel getMidiDeviceModel() { return deviceModel; }
	/**
	 * このデバイスフレームに貼り付けられたMIDIトランスミッタリストビューを取得します。
	 * @return MIDIトランスミッタリストビュー
	 */
	public MidiTransmitterListView getMidiTransmitterListView() { return transmitterListView; }
	/**
	 * このデバイスフレームに貼り付けられたMIDIトランシーバリストビューを取得します。
	 * @return MIDIトランシーバリストビュー
	 */
	public MidiReceiverListView getMidiReceiverListView() { return receiverListView; }
	/**
	 * ダイアログウィンドウがアクティブなときだけタイムスタンプ更新を有効にするためのリスナー
	 */
	public final WindowListener windowListener = new WindowAdapter() {
		@Override
		public void windowClosing(WindowEvent e) { timer.stop(); }
		@Override
		public void windowActivated(WindowEvent e) { timer.start(); }
	};
	/**
	 * MIDIデバイスモデルからフレームビューを構築します。
	 */
	public MidiDeviceFrame(MidiDeviceModel deviceModel, final MidiCablePane cablePane) {
		super( null, true, true, false, false );
		this.deviceModel = deviceModel;
		setTitle("[" + deviceModel.getMidiDeviceInOutType().getShortName() + "] " + deviceModel);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setLayout(new BorderLayout());
		add(new JLabel("--:--") {{
			timer = new Timer(50, new ActionListener() {
				private long sec = -2;
				private MidiDevice device = getMidiDeviceModel().getMidiDevice();
				@Override
				public void actionPerformed(ActionEvent event) {
					long usec = device.getMicrosecondPosition();
					long sec = (usec == -1 ? -1 : usec/1000000);
					if( sec == this.sec ) return;
					this.sec = sec;
					setText(sec == -1?"--:--":String.format("%02d:%02d",sec/60,sec%60));
					cablePane.repaint();
				}
			});
		}}, BorderLayout.SOUTH);
		add(scrollPane = new JScrollPane(trxPanel = new JPanel() {{
			setLayout(new BorderLayout());
			MidiDeviceModel.ReceiverListModel rxListModel = getMidiDeviceModel().getReceiverList();
			if( rxListModel != null ) {
				receiverListView = new MidiReceiverListView(rxListModel, cablePane);
				add(rxPanel = new JPanel() {{
					setLayout(new BorderLayout());
					add(new JLabel("Rx") {{ setVerticalAlignment(TOP); }}, BorderLayout.WEST);
					add(receiverListView);
				}}, BorderLayout.NORTH);
			}
			MidiDeviceModel.TransmitterListModel txListModel = getMidiDeviceModel().getTransmitterList();
			if( txListModel != null ) {
				transmitterListView = new MidiTransmitterListView(txListModel, cablePane);
				add(txPanel = new JPanel() {{
					setLayout(new BorderLayout());
					add(new JLabel("Tx") {{ setVerticalAlignment(TOP); }}, BorderLayout.WEST);
					add(transmitterListView);
				}}, rxListModel == null ? BorderLayout.NORTH : BorderLayout.SOUTH);
			}
		}}));
	}
	/**
	 * 引数で指定された{@link Transmitter}のセル範囲を示す、
	 * デスクトップの座標系内の境界の矩形を返します。対応するセルがない場合はnullを返します。
	 * @return セル範囲を示す境界の矩形、またはnull
	 */
	public Rectangle getBoundsOf(Transmitter tx) {
		if( transmitterListView == null ) return null;
		Rectangle rect = transmitterListView.getCellBounds(tx);
		translate(rect, txPanel, transmitterListView);
		return rect;
	}
	/**
	 * 引数で指定された{@link Receiver}のセル範囲を示す、
	 * デスクトップの座標系内の境界の矩形を返します。対応するセルがない場合はnullを返します。
	 * @return セル範囲を示す境界の矩形、またはnull
	 */
	public Rectangle getBoundsOf(Receiver rx) {
		if( receiverListView == null ) return null;
		Rectangle rect = receiverListView.getCellBounds(rx);
		translate(rect, rxPanel, receiverListView);
		return rect;
	}
	private void translate(Rectangle rect, JPanel panel, JList<? extends AutoCloseable> list) {
		if( rect == null ) return;
		int x = getX() + getRootPane().getX() + getContentPane().getX() +
				scrollPane.getX() + trxPanel.getX() +
				panel.getX() + list.getX();
		int y = getY() + getRootPane().getY() + getContentPane().getY() +
				scrollPane.getY() + trxPanel.getY() +
				panel.getY() + list.getY();
		rect.translate(x,y);
	}
}
