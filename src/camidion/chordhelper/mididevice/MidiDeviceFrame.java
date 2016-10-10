package camidion.chordhelper.mididevice;

import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;

/**
 * MIDIデバイスフレームビュー
 */
public class MidiDeviceFrame extends JInternalFrame {
	private static final String LABEL_NO_VALUE = "--:--";
	private MidiDeviceModel deviceModel;
	private TransmitterListView transmitterListView;
	private ReceiverListView receiverListView;
	private JScrollPane scrollPane;
	private JPanel trxPanel, txPanel, rxPanel;
	/**
	 * このデバイスフレームに表示内容を提供しているMIDIデバイスモデルを取得します。
	 * @return MIDIデバイスモデル
	 */
	public MidiDeviceModel getMidiDeviceModel() { return deviceModel; }
	/**
	 * MIDIデバイスモデルからフレームビューを構築します。
	 */
	public MidiDeviceFrame(MidiDeviceModel deviceModel, MidiCablePane cablePane) {
		super( null, true, true, false, false );
		this.deviceModel = deviceModel;
		setTitle("[" + deviceModel.getInOutType().getShortName() + "] " + deviceModel);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setLayout(new BorderLayout());
		add(new JLabel(LABEL_NO_VALUE) {
			Timer timer = new Timer(50, new ActionListener() {
				private long sec = -2;
				@Override
				public void actionPerformed(ActionEvent event) {
					if( ! isVisible() ) return;
					long usec = deviceModel.getMidiDevice().getMicrosecondPosition();
					long sec = (usec == -1 ? -1 : usec/1000000);
					if( sec == this.sec ) return;
					this.sec = sec;
					setText(sec == -1?LABEL_NO_VALUE:String.format("%02d:%02d",sec/60,sec%60));
					cablePane.repaint();
				}
			});
			{ timer.start(); }
		}, BorderLayout.SOUTH);
		add(scrollPane = new JScrollPane(trxPanel = new JPanel() {{
			setLayout(new BorderLayout());
			ReceiverListModel rxListModel = getMidiDeviceModel().getReceiverListModel();
			if( rxListModel != null ) {
				add(rxPanel = new JPanel() {{
					setLayout(new BorderLayout());
					add(new JLabel("Rx") {{ setVerticalAlignment(TOP); }}, BorderLayout.WEST);
					add(receiverListView = new ReceiverListView(rxListModel, cablePane));
				}}, BorderLayout.NORTH);
			}
			TransmitterListModel txListModel = getMidiDeviceModel().getTransmitterListModel();
			if( txListModel != null ) {
				add(txPanel = new JPanel() {{
					setLayout(new BorderLayout());
					add(new JLabel("Tx") {{ setVerticalAlignment(TOP); }}, BorderLayout.WEST);
					add(transmitterListView = new TransmitterListView(txListModel, cablePane));
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
		if( rect == null ) return null;
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
		if( rect == null ) return null;
		translate(rect, rxPanel, receiverListView);
		return rect;
	}
	private void translate(Rectangle rect, JPanel panel, AbstractTransceiverListView<?> list) {
		int x = getX() + getRootPane().getX() + getContentPane().getX() +
				scrollPane.getX() + trxPanel.getX() +
				panel.getX() + list.getX();
		int y = getY() + getRootPane().getY() + getContentPane().getY() +
				scrollPane.getY() + trxPanel.getY() +
				panel.getY() + list.getY();
		rect.translate(x,y);
	}
}
