
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyVetoException; // PropertyVetoException
import java.util.Hashtable;
import java.util.List;
import java.util.Vector; // Vector

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Transmitter;
import javax.swing.AbstractListModel;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.event.EventListenerList;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * 仮想MIDIデバイス
 */
interface VirtualMidiDevice extends MidiDevice {
	MidiChannel[] getChannels();
	void sendMidiMessage( MidiMessage msg );
	void setReceiver(Receiver rx);
}
/**
 * 仮想MIDIデバイスの最小限の実装を提供するクラス
 */
abstract class AbstractVirtualMidiDevice implements VirtualMidiDevice {
	/**
	 * この仮想デバイスのMIDIチャンネルの配列（MIDIメッセージ送信用）
	 */
	protected MidiChannelMessageSender[]
		channels = new MidiChannelMessageSender[MIDISpec.MAX_CHANNELS];
	/**
	 * 仮想MIDIデバイスを構築します。
	 */
	protected AbstractVirtualMidiDevice() {
		for( int i=0; i<channels.length; i++ )
			channels[i] = new MidiChannelMessageSender(this,i);
	}
	@Override
	public MidiChannel[] getChannels() { return channels; }
	/**
	 * MIDIデバイスを開いているときtrue
	 */
	protected boolean isOpen = false;
	/**
	 * 先頭のマイクロ秒位置（-1 で不定）
	 */
	protected long microsecondOrigin = -1;
	@Override
	public boolean isOpen() { return isOpen; }
	@Override
	public long getMicrosecondPosition() {
		return (microsecondOrigin == -1 ? -1: System.nanoTime()/1000 - microsecondOrigin);
	}
	@Override
	public void open() {
		isOpen = true;
		microsecondOrigin = System.nanoTime()/1000;
	}
	@Override
	public void close() {
		txList.clear();
		isOpen = false;
	}
	/**
	 * レシーバのリスト
	 */
	protected List<Receiver> rxList = new Vector<Receiver>();
	@Override
	public List<Receiver> getReceivers() { return rxList; }
	private int maxReceivers = 1;
	/**
	 * この MIDI デバイスで MIDI データを受信するのに使用可能な
	 *  MIDI IN 接続の最大数を設定します。デフォルト値は -1 です。
	 * @param maxReceivers MIDI IN 接続の最大数、または利用可能な接続数に制限がない場合は -1。
	 */
	protected void setMaxReceivers(int maxReceivers) {
		this.maxReceivers = maxReceivers;
	}
	@Override
	public int getMaxReceivers() { return maxReceivers; }
	@Override
	public Receiver getReceiver() {
		return rxList.isEmpty() ? null : rxList.get(0);
	}
	@Override
	public void setReceiver(Receiver rx) {
		if( maxReceivers == 0 )
			return;
		if( ! rxList.isEmpty() )
			rxList.clear();
		rxList.add(rx);
	}
	/**
	 * トランスミッタのリスト
	 */
	protected List<Transmitter> txList = new Vector<Transmitter>();
	@Override
	public List<Transmitter> getTransmitters() { return txList; }
	private int maxTransmitters = -1;
	@Override
	public int getMaxTransmitters() { return maxTransmitters; }
	/**
	 * この MIDI デバイスで MIDI データを転送するのに使用可能な
	 *  MIDI OUT 接続の最大数を設定します。デフォルト値は -1 です。
	 * @param maxTransmitters MIDI OUT 接続の最大数、または利用可能な接続数に制限がない場合は -1。
	 */
	protected void setMaxTransmitters(int maxTransmitters) {
		this.maxTransmitters = maxTransmitters;
	}
	@Override
	public Transmitter getTransmitter() throws MidiUnavailableException {
		if( maxTransmitters == 0 ) {
			throw new MidiUnavailableException();
		}
		Transmitter new_tx = new Transmitter() {
			private Receiver rx = null;
			public void close() { txList.remove(this); }
			public Receiver getReceiver() { return rx; }
			public void setReceiver(Receiver rx) { this.rx = rx; }
		};
		txList.add(new_tx);
		return new_tx;
	}
	@Override
	public void sendMidiMessage(MidiMessage msg) {
		long timestamp = getMicrosecondPosition();
		for( Transmitter tx : txList ) {
			Receiver rx = tx.getReceiver();
			if(rx != null) rx.send(msg, timestamp);
		}
	}
}

/**
 * 仮想MIDIデバイスからのMIDIチャンネルメッセージ送信クラス
 */
class MidiChannelMessageSender implements MidiChannel {
	/**
	 * このMIDIチャンネルの親となる仮想MIDIデバイス
	 */
	private VirtualMidiDevice vmd;
	/**
	 * MIDIチャンネルインデックス（チャンネル 1 のとき 0）
	 */
	private int channel;
	/**
	 * 指定の仮想MIDIデバイスの指定のMIDIチャンネルの
	 * メッセージを送信するためのインスタンスを構築します。
	 * @param vmd 仮想MIDIデバイス
	 * @param channel MIDIチャンネルインデックス（チャンネル 1 のとき 0）
	 */
	public MidiChannelMessageSender(VirtualMidiDevice vmd, int channel) {
		this.vmd = vmd;
		this.channel = channel;
	}
	/**
	 * 仮想MIDIデバイスからこのMIDIチャンネルのショートメッセージを送信します。
	 * @param command このメッセージで表される MIDI コマンド
	 * @param data1 第 1 データバイト
	 * @param data2 第 2 データバイト
	 * @see ShortMessage#setMessage(int, int, int, int)
	 */
	public void sendShortMessage(int command, int data1, int data2) {
		ShortMessage short_msg = new ShortMessage();
		try {
			short_msg.setMessage( command, channel, data1, data2 );
		} catch(InvalidMidiDataException e) {
			e.printStackTrace();
			return;
		}
		vmd.sendMidiMessage((MidiMessage)short_msg);
	}
	public void noteOff( int note_no ) { noteOff( note_no, 64 ); }
	public void noteOff( int note_no, int velocity ) {
		sendShortMessage( ShortMessage.NOTE_OFF, note_no, velocity );
	}
	public void noteOn( int note_no, int velocity ) {
		sendShortMessage( ShortMessage.NOTE_ON, note_no, velocity );
	}
	public void setPolyPressure(int note_no, int pressure) {
		sendShortMessage( ShortMessage.POLY_PRESSURE, note_no, pressure );
	}
	public int getPolyPressure(int noteNumber) { return 0x40; }
	public void controlChange(int controller, int value) {
		sendShortMessage( ShortMessage.CONTROL_CHANGE, controller, value );
	}
	public int getController(int controller) { return 0x40; }
	public void programChange( int program ) {
		sendShortMessage( ShortMessage.PROGRAM_CHANGE, program, 0 );
	}
	public void programChange(int bank, int program) {
		controlChange( 0x00, ((bank>>7) & 0x7F) );
		controlChange( 0x20, (bank & 0x7F) );
		programChange( program );
	}
	public int getProgram() { return 0; }
	public void setChannelPressure(int pressure) {
		sendShortMessage( ShortMessage.CHANNEL_PRESSURE, pressure, 0 );
	}
	public int getChannelPressure() { return 0x40; }
	public void setPitchBend(int bend) {
		// NOTE: Pitch Bend data byte order is Little Endian
		sendShortMessage(
			ShortMessage.PITCH_BEND,
			(bend & 0x7F), ((bend>>7) & 0x7F)
		);
	}
	public int getPitchBend() { return MIDISpec.PITCH_BEND_NONE; }
	public void allSoundOff() { controlChange( 0x78, 0 ); }
	public void resetAllControllers() { controlChange( 0x79, 0 ); }
	public boolean localControl(boolean on) {
		controlChange( 0x7A, on ? 0x7F : 0x00 );
		return false;
	}
	public void allNotesOff() { controlChange( 0x7B, 0 ); }
	public void setOmni(boolean on) {
		controlChange( on ? 0x7D : 0x7C, 0 );
	}
	public boolean getOmni() { return false; }
	public void setMono(boolean on) {}
	public boolean getMono() { return false; }
	public void setMute(boolean mute) {}
	public boolean getMute() { return false; }
	public void setSolo(boolean soloState) {}
	public boolean getSolo() { return false; }
}

/**
 * 仮想 MIDI デバイスからの MIDI 受信とチャンネル状態の管理
 */
abstract class AbstractMidiStatus extends Vector<AbstractMidiChannelStatus>
	implements Receiver
{
	private void resetStatus() { resetStatus(false); }
	private void resetStatus(boolean is_GS) {
		for( AbstractMidiChannelStatus mcs : this )
			mcs.resetAllValues(is_GS);
	}
	public void close() { }
	public void send(MidiMessage message, long timeStamp) {
		if ( message instanceof ShortMessage ) {
			ShortMessage sm = (ShortMessage)message;
			switch ( sm.getCommand() ) {
			case ShortMessage.NOTE_ON:
				get(sm.getChannel()).noteOn(sm.getData1(), sm.getData2());
				break;
			case ShortMessage.NOTE_OFF:
				get(sm.getChannel()).noteOff(sm.getData1(), sm.getData2());
				break;
			case ShortMessage.CONTROL_CHANGE:
				get(sm.getChannel()).controlChange(sm.getData1(), sm.getData2());
				break;
			case ShortMessage.PROGRAM_CHANGE:
				get(sm.getChannel()).programChange(sm.getData1());
				break;
			case ShortMessage.PITCH_BEND:
				{
					int b = (sm.getData1() & 0x7F);
					b += ((sm.getData2() & 0x7F) << 7);
					get(sm.getChannel()).setPitchBend(b);
				}
				break;
			case ShortMessage.POLY_PRESSURE:
				get(sm.getChannel()).setPolyPressure(sm.getData1(), sm.getData2());
				break;
			case ShortMessage.CHANNEL_PRESSURE:
				get(sm.getChannel()).setChannelPressure(sm.getData1());
				break;
			}
		}
		else if ( message instanceof SysexMessage ) {
			SysexMessage sxm = (SysexMessage)message;
			switch ( sxm.getStatus() ) {

			case SysexMessage.SYSTEM_EXCLUSIVE:
				byte data[] = sxm.getData();
				switch( data[0] ) {
				case 0x7E: // Non-Realtime Universal System Exclusive Message
					if( data[2] == 0x09 ) { // General MIDI (GM)
						if( data[3] == 0x01 ) { // GM System ON
							resetStatus();
						}
						else if( data[3] == 0x02 ) { // GM System OFF
							resetStatus();
						}
					}
					break;
				case 0x41: // Roland
					if( data[2]==0x42 && data[3]==0x12 ) { // GS DT1
						if( data[4]==0x40 && data[5]==0x00 && data[6]==0x7F &&
								data[7]==0x00 && data[8]==0x41
								) {
							resetStatus(true);
						}
						else if( data[4]==0x40 && (data[5] & 0xF0)==0x10 && data[6]==0x15 ) {
							// Drum Map 1 or 2, otherwise Normal Part
							boolean is_rhythm_part = ( data[7]==1 || data[7]==2 );
							int ch = (data[5] & 0x0F);
							if( ch == 0 ) ch = 9; else if( ch <= 9 ) ch--;
							get(ch).setRhythmPart(is_rhythm_part);
						}
						else if( data[4]==0x00 && data[5]==0x00 && data[6]==0x7F ) {
							if( data[7]==0x00 && data[8]==0x01 ) {
								// GM System Mode Set (1)
								resetStatus(true);
							}
							if( data[7]==0x01 && data[8]==0x00 ) {
								// GM System Mode Set (2)
								resetStatus(true);
							}
						}
					}
					break;
				case 0x43: // Yamaha
					if( data[2] == 0x4C
					&& data[3]==0 && data[4]==0 && data[5]==0x7E
					&& data[6]==0
							) {
						// XG System ON
						resetStatus();
					}
					break;
				}
				break;
			}
		}
	}
}
abstract class AbstractMidiChannelStatus implements MidiChannel {
	protected int channel;
	protected int program = 0;
	protected int pitchBend = MIDISpec.PITCH_BEND_NONE;
	protected int controllerValues[] = new int[0x80];
	protected boolean isRhythmPart = false;

	protected static final int DATA_NONE = 0;
	protected static final int DATA_FOR_RPN = 1;
	protected final int DATA_FOR_NRPN = 2;
	protected int dataFor = DATA_NONE;

	public AbstractMidiChannelStatus(int channel) {
		this.channel = channel;
		resetAllValues(true);
	}
	public int getChannel() { return channel; }
	public boolean isRhythmPart() { return isRhythmPart; }
	public void setRhythmPart(boolean is_rhythm_part) {
		this.isRhythmPart = is_rhythm_part;
	}
	public void resetRhythmPart() {
		isRhythmPart = (channel == 9);
	}
	public void resetAllValues() { resetAllValues(false); }
	public void resetAllValues(boolean is_GS) {
		for( int i=0; i<controllerValues.length; i++ )
			controllerValues[i] = 0;
		if( is_GS ) resetRhythmPart();
		resetAllControllers();
		controllerValues[10] = 0x40; // Set pan to center
	}
	public void fireRpnChanged() {}
	protected void changeRPNData( int data_diff ) {
		int dataMsb = controllerValues[0x06];
		int dataLsb = controllerValues[0x26];
		if( data_diff != 0 ) {
			// Data increment or decrement
			dataLsb += data_diff;
			if( dataLsb >= 100 ) {
				dataLsb = 0;
				controllerValues[0x26] = ++dataMsb;
			}
			else if( dataLsb < 0 ) {
				dataLsb = 0;
				controllerValues[0x26] = --dataMsb;
			}
			controllerValues[0x06] = dataLsb;
		}
		fireRpnChanged();
	}
	@Override
	public void noteOff( int noteNumber ) {}
	@Override
	public void noteOff( int noteNumber, int velocity ) {}
	@Override
	public void noteOn( int noteNumber, int velocity ) {}
	@Override
	public int getController(int controller) {
		return controllerValues[controller];
	}
	@Override
	public void programChange( int program ) {
		this.program = program;
	}
	@Override
	public void programChange(int bank, int program) {
		controlChange( 0x00, ((bank>>7) & 0x7F) );
		controlChange( 0x20, (bank & 0x7F) );
		programChange( program );
	}
	@Override
	public int getProgram() { return program; }
	@Override
	public void setPitchBend(int bend) { pitchBend = bend; }
	@Override
	public int getPitchBend() { return pitchBend; }
	@Override
	public void setPolyPressure(int noteNumber, int pressure) {}
	@Override
	public int getPolyPressure(int noteNumber) { return 0x40; }
	@Override
	public void setChannelPressure(int pressure) {}
	@Override
	public int getChannelPressure() { return 0x40; }
	@Override
	public void allSoundOff() {}
	@Override
	public void allNotesOff() {}
	@Override
	public void resetAllControllers() {
		//
		// See also:
		//   Recommended Practice (RP-015)
		//   Response to Reset All Controllers
		//   http://www.midi.org/techspecs/rp15.php
		//
		// modulation
		controllerValues[0] = 0;
		//
		// pedals
		for(int i=64; i<=67; i++) controllerValues[i] = 0;
		//
		// Set pitch bend to center
		pitchBend = 8192;
		//
		// Set NRPN / RPN to null value
		for(int i=98; i<=101; i++) controllerValues[i] = 127;
	}
	@Override
	public boolean localControl(boolean on) {
		controlChange( 0x7A, on ? 0x7F : 0x00 );
		return false;
	}
	@Override
	public void setOmni(boolean on) {
		controlChange( on ? 0x7D : 0x7C, 0 );
	}
	@Override
	public boolean getOmni() { return false; }
	@Override
	public void setMono(boolean on) {}
	@Override
	public boolean getMono() { return false; }
	@Override
	public void setMute(boolean mute) {}
	@Override
	public boolean getMute() { return false; }
	@Override
	public void setSolo(boolean soloState) {}
	@Override
	public boolean getSolo() { return false; }
	@Override
	public void controlChange(int controller, int value) {
		controllerValues[controller] = value & 0x7F;
		switch( controller ) {

		case 0x78: // All Sound Off
			allSoundOff();
			break;

		case 0x7B: // All Notes Off
			allNotesOff();
			break;

		case 0x79: // Reset All Controllers
			resetAllControllers();
			break;

		case 0x06: // Data Entry (MSB)
		case 0x26: // Data Entry (LSB)
			changeRPNData(0);
			break;

		case 0x60: // Data Increment
			changeRPNData(1);
			break;

		case 0x61: // Data Decrement
			changeRPNData(-1);
			break;

			// Non-Registered Parameter Number
		case 0x62: // NRPN (LSB)
		case 0x63: // NRPN (MSB)
			dataFor = DATA_FOR_NRPN;
			// fireRpnChanged();
			break;

			// Registered Parameter Number
		case 0x64: // RPN (LSB)
		case 0x65: // RPN (MSB)
			dataFor = DATA_FOR_RPN;
			fireRpnChanged();
			break;
		}
	}
}

/**
 * Transmitter(Tx)/Receiver(Rx) のリスト（view）
 *
 * <p>マウスで Tx からドラッグして Rx へドロップする機能を備えた
 * 仮想MIDI端子リストです。
 * </p>
 */
class MidiConnecterListView extends JList<AutoCloseable>
	implements Transferable, DragGestureListener, DropTargetListener
{
	public static final Icon MIDI_CONNECTER_ICON =
		new ButtonIcon(ButtonIcon.MIDI_CONNECTOR_ICON);
	private class CellRenderer extends JLabel implements ListCellRenderer<AutoCloseable> {
		public Component getListCellRendererComponent(
			JList<? extends AutoCloseable> list,
			AutoCloseable value,
			int index,
			boolean isSelected,
			boolean cellHasFocus
		) {
			String text;
			if( value instanceof Transmitter ) text = "Tx";
			else if( value instanceof Receiver ) text = "Rx";
			else text = (value==null ? null : value.toString());
			setText(text);
			setIcon(MIDI_CONNECTER_ICON);
			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			} else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}
			setEnabled(list.isEnabled());
			setFont(list.getFont());
			setOpaque(true);
			return this;
		}
	}
	/**
	 * 仮想MIDI端子リストビューを生成します。
	 * @param model このビューから参照されるデータモデル
	 */
	public MidiConnecterListView(MidiConnecterListModel model) {
		super(model);
		setCellRenderer(new CellRenderer());
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setLayoutOrientation(JList.HORIZONTAL_WRAP);
		setVisibleRowCount(0);
        (new DragSource()).createDefaultDragGestureRecognizer(
        	this, DnDConstants.ACTION_COPY_OR_MOVE, this
        );
		new DropTarget( this, DnDConstants.ACTION_COPY_OR_MOVE, this, true );
	}
	private static final DataFlavor transmitterFlavor =
		new DataFlavor(Transmitter.class, "Transmitter");
	private static final DataFlavor transmitterFlavors[] = {transmitterFlavor};
	@Override
	public Object getTransferData(DataFlavor flavor) {
		return getModel().getElementAt(getSelectedIndex());
	}
	@Override
	public DataFlavor[] getTransferDataFlavors() {
		return transmitterFlavors;
	}
	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		return flavor.equals(transmitterFlavor);
	}
	@Override
	public void dragGestureRecognized(DragGestureEvent dge) {
		int action = dge.getDragAction();
		if( (action & DnDConstants.ACTION_COPY_OR_MOVE) == 0 )
			return;
		int index = locationToIndex(dge.getDragOrigin());
		AutoCloseable data = getModel().getElementAt(index);
		if( data instanceof Transmitter ) {
			dge.startDrag(DragSource.DefaultLinkDrop, this, null);
		}
	}
	@Override
	public void dragEnter(DropTargetDragEvent event) {
		if( event.isDataFlavorSupported(transmitterFlavor) )
			event.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
	}
	@Override
	public void dragExit(DropTargetEvent dte) {}
	@Override
	public void dragOver(DropTargetDragEvent dtde) {}
	@Override
	public void dropActionChanged(DropTargetDragEvent dtde) {}
	@Override
	public void drop(DropTargetDropEvent event) {
		event.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
		try {
			int maskedBits = event.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE;
			if( maskedBits != 0 ) {
				Transferable t = event.getTransferable();
				Object data = t.getTransferData(transmitterFlavor);
				if( data instanceof Transmitter ) {
					getModel().ConnectToReceiver((Transmitter)data);
					event.dropComplete(true);
					return;
				}
			}
			event.dropComplete(false);
		}
		catch (Exception ex) {
			ex.printStackTrace();
			event.dropComplete(false);
		}
	}
	@Override
	public MidiConnecterListModel getModel() {
		return (MidiConnecterListModel)super.getModel();
	}
	/**
	 * 選択されているトランスミッタを閉じます。
	 * レシーバが選択されていた場合は無視されます。
	 */
	public void closeSelectedTransmitter() {
		AutoCloseable ac = getSelectedValue();
		if( ac instanceof Transmitter )
			getModel().closeTransmitter((Transmitter)ac);
	}
}

/**
 * １個の MIDI デバイスに属する Transmitter/Receiver のリストモデル
 */
class MidiConnecterListModel extends AbstractListModel<AutoCloseable> {
	protected MidiDevice device;
	private List<MidiConnecterListModel> modelList;
	/**
	 * 指定のMIDIデバイスに属する
	 *  {@link Transmitter}/{@link Receiver} のリストモデルを構築します。
	 *
	 * @param device 対象MIDIデバイス
	 * @param modelList リストモデルのリスト
	 */
	public MidiConnecterListModel(MidiDevice device, List<MidiConnecterListModel> modelList) {
		this.device = device;
		this.modelList = modelList;
	}
	/**
	 * 対象MIDIデバイスを返します。
	 * @return 対象MIDIデバイス
	 */
	public MidiDevice getMidiDevice() {
		return device;
	}
	/**
	 * 対象MIDIデバイスの名前を返します。
	 */
	public String toString() {
		return device.getDeviceInfo().toString();
	}
	@Override
	public AutoCloseable getElementAt(int index) {
		List<Receiver> rxList = device.getReceivers();
		int rxSize = rxList.size();
		if( index < rxSize ) return rxList.get(index);
		index -= rxSize;
		List<Transmitter> txList = device.getTransmitters();
		return index < txList.size() ? txList.get(index) : null;
	}
	@Override
	public int getSize() {
		return
			device.getReceivers().size() +
			device.getTransmitters().size();
	}
	/**
	 * 指定の要素がこのリストモデルで最初に見つかった位置を返します。
	 *
	 * @param element 探したい要素
	 * @return 位置のインデックス（先頭が 0、見つからないとき -1）
	 */
	public int indexOf(AutoCloseable element) {
		List<Receiver> rxList = device.getReceivers();
		int index = rxList.indexOf(element);
		if( index < 0 ) {
			List<Transmitter> txList = device.getTransmitters();
			if( (index = txList.indexOf(element)) >= 0 )
				index += rxList.size();
		}
		return index;
	}
	/**
	 * このリストが {@link Transmitter} をサポートしているか調べます。
	 * @return {@link Transmitter} をサポートしていたら true
	 */
	public boolean txSupported() {
		return device.getMaxTransmitters() != 0;
	}
	/**
	 * このリストが {@link Receiver} をサポートしているか調べます。
	 * @return {@link Receiver} をサポートしていたら true
	 */
	public boolean rxSupported() {
		return device.getMaxReceivers() != 0;
	}
	/**
	 * このリストのMIDIデバイスの入出力タイプを返します。
	 * <p>レシーバからMIDI信号を受けて外部へ出力できるデバイスの場合は MIDI_OUT、
	 * 外部からMIDI信号を入力してトランスミッタからレシーバへ転送できるデバイスの場合は MIDI_IN、
	 * 両方できるデバイスの場合は MIDI_IN_OUT を返します。
	 * </p>
	 * @return このリストのMIDIデバイスの入出力タイプ
	 */
	public MidiDeviceInOutType getMidiDeviceInOutType() {
		if( rxSupported() ) {
			if( txSupported() )
				return MidiDeviceInOutType.MIDI_IN_OUT;
			else
				return MidiDeviceInOutType.MIDI_OUT;
		}
		else {
			if( txSupported() )
				return MidiDeviceInOutType.MIDI_IN;
			else
				return null;
		}
	}
	/**
	 * 引数で指定されたトランスミッタを、最初のレシーバに接続します。
	 * <p>接続先のレシーバがない場合は無視されます。
	 * </p>
	 * @param tx トランスミッタ
	 */
	public void ConnectToReceiver(Transmitter tx) {
		List<Receiver> receivers = device.getReceivers();
		if( receivers.size() == 0 )
			return;
		tx.setReceiver(receivers.get(0));
		fireContentsChanged(this,0,getSize());
	}
	/**
	 * 未接続のトランスミッタを、
	 * 引数で指定されたリストモデルの最初のレシーバに接続します。
	 * @param anotherModel 接続先レシーバを持つリストモデル
	 */
	public void connectToReceiverOf(MidiConnecterListModel anotherModel) {
		if( ! txSupported() )
			return;
		if( anotherModel == null || ! anotherModel.rxSupported() )
			return;
		List<Receiver> rxList = anotherModel.device.getReceivers();
		if( rxList.isEmpty() )
			return;
		getUnconnectedTransmitter().setReceiver(rxList.get(0));
	}
	/**
	 * レシーバに未接続の最初のトランスミッタを返します。
	 * @return 未接続のトランスミッタ
	 */
	public Transmitter getUnconnectedTransmitter() {
		if( ! txSupported() ) {
			return null;
		}
		List<Transmitter> txList = device.getTransmitters();
		for( Transmitter tx : txList ) {
			if( tx.getReceiver() == null )
				return tx;
		}
		Transmitter tx;
		try {
			tx = device.getTransmitter();
		} catch( MidiUnavailableException e ) {
			e.printStackTrace();
			return null;
		}
		fireIntervalAdded(this,0,getSize());
		return tx;
	}
	/**
	 * 指定のトランスミッタを閉じます。
	 * <p>このリストモデルにないトランスミッタが指定された場合、無視されます。
	 * </p>
	 * @param txToClose 閉じたいトランスミッタ
	 */
	public void closeTransmitter(Transmitter txToClose) {
		if( device.getTransmitters().contains(txToClose) ) {
			txToClose.close();
			fireIntervalRemoved(this,0,getSize());
		}
	}
	/**
	 * 対象MIDIデバイスを開きます。
	 * @throws MidiUnavailableException デバイスを開くことができない場合
	 */
	public void openDevice() throws MidiUnavailableException {
		device.open();
		if( rxSupported() && device.getReceivers().size() == 0 ) {
			device.getReceiver();
		}
	}
	/**
	 * 対象MIDIデバイスを閉じます。
	 *
	 * <p>対象MIDIデバイスの Receiver を設定している
	 *  {@link Transmitter} があればすべて閉じます。
	 * </p>
	 */
	public void closeDevice() {
		if( rxSupported() ) {
			Receiver rx = device.getReceivers().get(0);
			for( MidiConnecterListModel m : modelList ) {
				if( m == this || ! m.txSupported() )
					continue;
				for( int i=0; i<m.getSize(); i++ ) {
					AutoCloseable ac = m.getElementAt(i);
					if( ! (ac instanceof Transmitter) )
						continue;
					Transmitter tx = ((Transmitter)ac);
					if( tx.getReceiver() == rx )
						m.closeTransmitter(tx);
				}
			}
		}
		device.close();
	}
	/**
	 * マイクロ秒位置をリセットします。
	 * <p>マイクロ秒位置はMIDIデバイスを開いてからの時間で表されます。
	 * このメソッドではMIDIデバイスをいったん閉じて再び開くことによって
	 * 時間位置をリセットします。
	 * 接続相手のデバイスがあった場合、元通りに接続を復元します。
	 * </p>
	 * <p>MIDIデバイスからリアルタイムレコーディングを開始するときは、
	 * 必ずマイクロ秒位置をリセットする必要があります。
	 * （リセットされていないマイクロ秒位置がそのままシーケンサに記録されると、
	 * 大幅に後ろのほうにずれて記録されてしまいます）
	 * </p>
	 */
	public void resetMicrosecondPosition() {
		if( ! txSupported() || device instanceof Sequencer )
			return;
		//
		// デバイスを閉じる前に接続相手の情報を保存
		List<Transmitter> txList = device.getTransmitters();
		List<Receiver> peerRxList = new Vector<Receiver>();
		for( Transmitter tx : txList ) {
			Receiver rx = tx.getReceiver();
			if( rx != null ) peerRxList.add(rx);
		}
		List<Transmitter> peerTxList = null;
		Receiver rx = null;
		if( rxSupported() ) {
			rx = device.getReceivers().get(0);
			peerTxList = new Vector<Transmitter>();
			for( MidiConnecterListModel m : modelList ) {
				if( m == this || ! m.txSupported() )
					continue;
				for( int i=0; i<m.getSize(); i++ ) {
					Object obj = m.getElementAt(i);
					if( ! (obj instanceof Transmitter) )
						continue;
					Transmitter tx = ((Transmitter)obj);
					if( tx.getReceiver() == rx )
						peerTxList.add(tx);
				}
			}
		}
		// いったん閉じて開く（ここでマイクロ秒位置がリセットされる）
		device.close();
		try {
			device.open();
		} catch( MidiUnavailableException e ) {
			e.printStackTrace();
		}
		// 元通りに接続し直す
		for( Receiver peerRx : peerRxList ) {
			Transmitter tx = getUnconnectedTransmitter();
			if( tx == null ) continue;
			tx.setReceiver(peerRx);
		}
		if( peerTxList != null ) {
			rx = device.getReceivers().get(0);
			for( Transmitter peerTx : peerTxList ) {
				peerTx.setReceiver(rx);
			}
		}
	}
}

/**
 * MIDIデバイスフレームビュー
 */
class MidiDeviceFrame extends JInternalFrame {
	private static Insets ZERO_INSETS = new Insets(0,0,0,0);
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
			if( listView.getModel().txSupported() ) {
				add(new JButton("New Tx") {{
					setMargin(ZERO_INSETS);
					addActionListener(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent event) {
							listView.getModel().getUnconnectedTransmitter();
						}
					});
				}});
				add(new JButton("Close Tx") {{
					setMargin(ZERO_INSETS);
					addActionListener(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent event) {
							listView.closeSelectedTransmitter();
						}
					});
				}});
			}
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
		setSize(250,100);
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

/**
 * MIDIデバイス入出力タイプ
 */
enum MidiDeviceInOutType {
	MIDI_OUT("MIDI output devices (MIDI synthesizer etc.)"),
	MIDI_IN("MIDI input devices (MIDI keyboard etc.)"),
	MIDI_IN_OUT("MIDI input/output devices (MIDI sequencer etc.)");
	private String description;
	private MidiDeviceInOutType(String description) {
		this.description = description;
	}
	public String getDescription() {
		return description;
	}
}
/**
 * MIDIデバイスツリーモデル
 */
class MidiDeviceTreeModel implements TreeModel {
	List<MidiConnecterListModel> deviceModelList;
	public MidiDeviceTreeModel(List<MidiConnecterListModel> deviceModelList) {
		this.deviceModelList = deviceModelList;
	}
	public Object getRoot() {
		return "MIDI devices";
	}
	public Object getChild(Object parent, int index) {
		if( parent == getRoot() ) {
			return MidiDeviceInOutType.values()[index];
		}
		if( parent instanceof MidiDeviceInOutType ) {
			MidiDeviceInOutType ioType = (MidiDeviceInOutType)parent;
			for( MidiConnecterListModel deviceModel : deviceModelList )
				if( deviceModel.getMidiDeviceInOutType() == ioType ) {
					if( index == 0 )
						return deviceModel;
					index--;
				}
		}
		return null;
	}
	public int getChildCount(Object parent) {
		if( parent == getRoot() ) {
			return MidiDeviceInOutType.values().length;
		}
		int childCount = 0;
		if( parent instanceof MidiDeviceInOutType ) {
			MidiDeviceInOutType ioType = (MidiDeviceInOutType)parent;
			for( MidiConnecterListModel deviceModel : deviceModelList )
				if( deviceModel.getMidiDeviceInOutType() == ioType )
					childCount++;
		}
		return childCount;
	}
	public int getIndexOfChild(Object parent, Object child) {
		if( parent == getRoot() ) {
			if( child instanceof MidiDeviceInOutType ) {
				MidiDeviceInOutType ioType = (MidiDeviceInOutType)child;
				return ioType.ordinal();
			}
		}
		if( parent instanceof MidiDeviceInOutType ) {
			MidiDeviceInOutType ioType = (MidiDeviceInOutType)parent;
			int index = 0;
			for( MidiConnecterListModel deviceModel : deviceModelList ) {
				if( deviceModel.getMidiDeviceInOutType() == ioType ) {
					if( deviceModel == child )
						return index;
					index++;
				}
			}
		}
		return -1;
	}
	public boolean isLeaf(Object node) {
		return node instanceof MidiConnecterListModel;
	}
	public void valueForPathChanged(TreePath path, Object newValue) {}
	private EventListenerList listenerList = new EventListenerList();
	public void addTreeModelListener(TreeModelListener listener) {
		listenerList.add(TreeModelListener.class, listener);
	}
	public void removeTreeModelListener(TreeModelListener listener) {
		listenerList.remove(TreeModelListener.class, listener);
	}
	public void fireTreeNodesChanged(
		Object source, Object[] path, int[] childIndices, Object[] children
	) {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==TreeModelListener.class) {
				((TreeModelListener)listeners[i+1]).treeNodesChanged(
					new TreeModelEvent(source,path,childIndices,children)
				);
			}
		}
	}
}

/**
 * MIDIデバイスツリー (View)
 */
class MidiDeviceTree extends JTree
	implements Transferable, DragGestureListener, InternalFrameListener
{
	/**
	 * MIDIデバイスツリーのビューを構築します。
	 * @param model このビューにデータを提供するモデル
	 */
	public MidiDeviceTree(MidiDeviceTreeModel model) {
		super(model);
        (new DragSource()).createDefaultDragGestureRecognizer(
        	this, DnDConstants.ACTION_COPY_OR_MOVE, this
        );
        setCellRenderer(new DefaultTreeCellRenderer() {
    		@Override
    		public Component getTreeCellRendererComponent(
    			JTree tree, Object value,
    			boolean selected, boolean expanded, boolean leaf, int row,
    			boolean hasFocus
    		) {
    			super.getTreeCellRendererComponent(
    				tree, value, selected, expanded, leaf, row, hasFocus
    			);
    			if(leaf) {
   					setIcon(MidiConnecterListView.MIDI_CONNECTER_ICON);
   					setDisabledIcon(MidiConnecterListView.MIDI_CONNECTER_ICON);
    				MidiConnecterListModel listModel = (MidiConnecterListModel)value;
    				setEnabled( ! listModel.getMidiDevice().isOpen() );
    			}
    			return this;
    		}
    	});
	}
	/**
	 * このデバイスツリーからドラッグされるデータフレーバ
	 */
	public static final DataFlavor
		treeModelFlavor = new DataFlavor(TreeModel.class, "TreeModel");
	private static final DataFlavor treeModelFlavors[] = {treeModelFlavor};
	@Override
	public Object getTransferData(DataFlavor flavor) {
		return getLastSelectedPathComponent();
	}
	@Override
	public DataFlavor[] getTransferDataFlavors() {
		return treeModelFlavors;
	}
	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		return flavor.equals(treeModelFlavor);
	}
	@Override
	public void dragGestureRecognized(DragGestureEvent dge) {
		int action = dge.getDragAction();
		if( (action & DnDConstants.ACTION_COPY_OR_MOVE) != 0 ) {
			dge.startDrag(DragSource.DefaultMoveDrop, this, null);
		}
	}
	@Override
	public void internalFrameOpened(InternalFrameEvent e) {}
	/**
	 * 	MidiDeviceFrame のクローズ処理中に再描画リクエストを送ります。
	 */
	@Override
	public void internalFrameClosing(InternalFrameEvent e) {
		repaint();
	}
	@Override
	public void internalFrameClosed(InternalFrameEvent e) {}
	@Override
	public void internalFrameIconified(InternalFrameEvent e) {}
	@Override
	public void internalFrameDeiconified(InternalFrameEvent e) {}
	@Override
	public void internalFrameActivated(InternalFrameEvent e) {}
	@Override
	public void internalFrameDeactivated(InternalFrameEvent e) {}
}

/**
 * MIDIデバイスモデルリスト
 */
class MidiDeviceModelList extends Vector<MidiConnecterListModel> {
	/**
	 * MIDIシーケンサーモデル
	 */
	MidiSequencerModel sequencerModel;
	/**
	 * MIDIエディタ
	 */
	MidiEditor editorDialog;
	/**
	 * MIDIシンセサイザーモデル
	 */
	private MidiConnecterListModel editorDialogModel;
	/**
	 * MIDIシンセサイザーモデル
	 */
	private MidiConnecterListModel synthModel;
	/**
	 * 最初のMIDI出力
	 */
	private MidiConnecterListModel firstMidiOutModel;
	/**
	 * MIDIデバイスモデルリストを生成します。
	 * @param vmdList 仮想MIDIデバイスのリスト
	 */
	public MidiDeviceModelList(List<VirtualMidiDevice> vmdList) {
		MidiDevice.Info[] devInfos = MidiSystem.getMidiDeviceInfo();
		MidiConnecterListModel guiModels[] = new MidiConnecterListModel[vmdList.size()];
		MidiConnecterListModel firstMidiInModel = null;
		for( int i=0; i<vmdList.size(); i++ )
			guiModels[i] = addMidiDevice(vmdList.get(i));
		Sequencer sequencer;
		try {
			sequencer = MidiSystem.getSequencer(false);
			sequencerModel = (MidiSequencerModel)addMidiDevice(sequencer);
		} catch( MidiUnavailableException e ) {
			System.out.println(
				ChordHelperApplet.VersionInfo.NAME +
				" : MIDI sequencer unavailable"
			);
			e.printStackTrace();
		}
		editorDialog = new MidiEditor(sequencerModel);
		editorDialogModel = addMidiDevice(editorDialog.virtualMidiDevice);
		for( MidiDevice.Info info : devInfos ) {
			MidiDevice device;
			try {
				device = MidiSystem.getMidiDevice(info);
			} catch( MidiUnavailableException e ) {
				e.printStackTrace(); continue;
			}
			if( device instanceof Sequencer ) continue;
			if( device instanceof Synthesizer ) {
				try {
					synthModel = addMidiDevice(MidiSystem.getSynthesizer());
				} catch( MidiUnavailableException e ) {
					System.out.println(
						ChordHelperApplet.VersionInfo.NAME +
						" : Java internal MIDI synthesizer unavailable"
					);
					e.printStackTrace();
				}
				continue;
			}
			MidiConnecterListModel m = addMidiDevice(device);
			if( m.rxSupported() && firstMidiOutModel == null )
				firstMidiOutModel = m;
			if( m.txSupported() && firstMidiInModel == null )
				firstMidiInModel = m;
		}
		// デバイスを開く。
		//   NOTE: 必ず MIDI OUT Rx デバイスを先に開くこと。
		//
		//   そうすれば、後から開いた MIDI IN Tx デバイスからの
		//   タイムスタンプのほうが「若く」なる。これにより、
		//   先に開かれ「少し歳を食った」Rx デバイスは
		//   「信号が遅れてやってきた」と認識するので、
		//   遅れを取り戻そうとして即座に音を出してくれる。
		//
		//   開く順序が逆になると「進みすぎるから遅らせよう」として
		//   無用なレイテンシーが発生する原因になる。
		try {
			MidiConnecterListModel openModels[] = {
				synthModel,
				firstMidiOutModel,
				sequencerModel,
				firstMidiInModel,
				editorDialogModel,
			};
			for( MidiConnecterListModel m : openModels ) {
				if( m != null ) m.openDevice();
			}
			for( MidiConnecterListModel m : guiModels ) {
				m.openDevice();
			}
		} catch( MidiUnavailableException ex ) {
			ex.printStackTrace();
		}
		// 初期接続
		//
		for( MidiConnecterListModel mtx : guiModels ) {
			for( MidiConnecterListModel mrx : guiModels )
				mtx.connectToReceiverOf(mrx);
			mtx.connectToReceiverOf(sequencerModel);
			mtx.connectToReceiverOf(synthModel);
			mtx.connectToReceiverOf(firstMidiOutModel);
		}
		if( firstMidiInModel != null ) {
			for( MidiConnecterListModel m : guiModels )
				firstMidiInModel.connectToReceiverOf(m);
			firstMidiInModel.connectToReceiverOf(sequencerModel);
			firstMidiInModel.connectToReceiverOf(synthModel);
			firstMidiInModel.connectToReceiverOf(firstMidiOutModel);
		}
		if( sequencerModel != null ) {
			for( MidiConnecterListModel m : guiModels )
				sequencerModel.connectToReceiverOf(m);
			sequencerModel.connectToReceiverOf(synthModel);
			sequencerModel.connectToReceiverOf(firstMidiOutModel);
		}
		if( editorDialogModel != null ) {
			editorDialogModel.connectToReceiverOf(synthModel);
			editorDialogModel.connectToReceiverOf(firstMidiOutModel);
		}
	}
	/**
	 * 指定のMIDIデバイスからMIDIデバイスモデルを生成して追加します。
	 * @param device MIDIデバイス
	 * @return 生成されたMIDIデバイスモデル
	 */
	private MidiConnecterListModel addMidiDevice(MidiDevice device) {
		MidiConnecterListModel m;
		if( device instanceof Sequencer )
			m = new MidiSequencerModel(this,(Sequencer)device,this);
		else
			m = new MidiConnecterListModel(device,this);
		addElement(m);
		return m;
	}
}

/**
 * MIDIデバイスダイアログ (View)
 */
class MidiDeviceDialog extends JDialog implements ActionListener {
	private JEditorPane deviceInfoPane;
	private MidiDesktopPane desktopPane;
	public MidiDeviceDialog(List<MidiConnecterListModel> deviceModelList) {
		setTitle("MIDI device connection");
		setBounds( 300, 300, 800, 500 );
		MidiDeviceTreeModel treeModel = new MidiDeviceTreeModel(deviceModelList);
		MidiDeviceTree deviceTree = new MidiDeviceTree(treeModel) {{
			addTreeSelectionListener(new TreeSelectionListener() {
				@Override
				public void valueChanged(TreeSelectionEvent e) {
					String html = "<html><head></head><body>";
					Object obj = getLastSelectedPathComponent();
					if( obj instanceof MidiConnecterListModel ) {
						MidiConnecterListModel deviceModel = (MidiConnecterListModel)obj;
						MidiDevice.Info info = deviceModel.getMidiDevice().getDeviceInfo();
						html += "<b>"+deviceModel+"</b><br/>";
						html += "<table border=\"1\"><tbody>";
						html += "<tr><th>Version</th><td>"+info.getVersion()+"</td></tr>";
						html += "<tr><th>Description</th><td>"+info.getDescription()+"</td></tr>";
						html += "<tr><th>Vendor</th><td>"+info.getVendor()+"</td></tr>";
						html += "</tbody></table>";
						MidiDeviceFrame frame = desktopPane.getFrameOf(deviceModel);
						if( frame != null ) {
							try {
								frame.setSelected(true);
							} catch( PropertyVetoException ex ) {
								ex.printStackTrace();
							}
						}
					}
					else if( obj instanceof MidiDeviceInOutType ) {
						MidiDeviceInOutType ioType = (MidiDeviceInOutType)obj;
						html += "<b>"+ioType+"</b><br/>";
						html += ioType.getDescription()+"<br/>";
					}
					else if( obj != null ) {
						html += obj.toString();
					}
					html += "</body></html>";
					deviceInfoPane.setText(html);
				}
			});
		}};
		add(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
			new JSplitPane(JSplitPane.VERTICAL_SPLIT,
				new JScrollPane(deviceTree),
				new JScrollPane(deviceInfoPane = new JEditorPane("text/html","<html></html>") {{
					setEditable(false);
				}})
			){{
				setDividerLocation(260);
			}},
			desktopPane = new MidiDesktopPane(deviceTree)
		){{
			setOneTouchExpandable(true);
			setDividerLocation(250);
		}});
		addWindowListener(new WindowListener() {
			@Override
			public void windowOpened(WindowEvent e) {
			}
			@Override
			public void windowClosing(WindowEvent e) {
				desktopPane.setAllDeviceTimestampTimers(false);
			}
			@Override
			public void windowClosed(WindowEvent e) {
			}
			@Override
			public void windowIconified(WindowEvent e) {
			}
			@Override
			public void windowDeiconified(WindowEvent e) {
			}
			@Override
			public void windowActivated(WindowEvent e) {
				desktopPane.setAllDeviceTimestampTimers(true);
			}
			@Override
			public void windowDeactivated(WindowEvent e) {
			}
		});
	}
	@Override
	public void actionPerformed(ActionEvent event) {
		setVisible(true);
	}
}

/**
 * 開いている MIDI デバイスを置くためのデスクトップ (View)
 */
class MidiDesktopPane extends JDesktopPane implements DropTargetListener {
	private MidiCablePane cablePane = new MidiCablePane(this);
	public MidiDesktopPane(MidiDeviceTree deviceTree) {
		add(cablePane, JLayeredPane.PALETTE_LAYER);
		int i=0;
		MidiDeviceTreeModel treeModel = (MidiDeviceTreeModel)deviceTree.getModel();
		List<MidiConnecterListModel> deviceModelList = treeModel.deviceModelList;
		for( MidiConnecterListModel deviceModel : deviceModelList ) {
			MidiDeviceFrame frame = new MidiDeviceFrame(deviceModel) {
				{
					addInternalFrameListener(cablePane);
					addComponentListener(cablePane);
				}
			};
			frame.addInternalFrameListener(deviceTree);
			deviceModel.addListDataListener(cablePane);
			add(frame);
			if( deviceModel.getMidiDevice().isOpen() ) {
				frame.setBounds( 10+(i%2)*260, 10+i*55, 250, 100 );
				frame.setVisible(true);
				i++;
			}
		}
		addComponentListener(
			new ComponentAdapter() {
				public void componentResized(ComponentEvent e) {
					cablePane.setSize(getSize());
				}
			}
		);
		new DropTarget( this, DnDConstants.ACTION_COPY_OR_MOVE, this, true );
	}
	public void dragEnter(DropTargetDragEvent dtde) {
		Transferable trans = dtde.getTransferable();
		if( trans.isDataFlavorSupported(MidiDeviceTree.treeModelFlavor) ) {
			dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
		}
	}
	public void dragExit(DropTargetEvent dte) {}
	public void dragOver(DropTargetDragEvent dtde) {}
	public void dropActionChanged(DropTargetDragEvent dtde) {}
	public void drop(DropTargetDropEvent dtde) {
		dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
		try {
			int action = dtde.getDropAction() ;
			if( (action & DnDConstants.ACTION_COPY_OR_MOVE) != 0 ) {
				Transferable trans = dtde.getTransferable();
				Object data = trans.getTransferData(MidiDeviceTree.treeModelFlavor);
				if( data instanceof MidiConnecterListModel ) {
					MidiConnecterListModel deviceModel = (MidiConnecterListModel)data;
					try {
						deviceModel.openDevice();
					} catch( MidiUnavailableException e ) {
						//
						// デバイスを開くのに失敗した場合
						//
						//   例えば、「Microsort MIDI マッパー」と
						//   「Microsoft GS Wavetable SW Synth」を
						//   同時に開こうとするとここに来る。
						//
						dtde.dropComplete(false);
						String message = "MIDIデバイス "
								+ deviceModel
								+" を開けません。\n"
								+ "すでに開かれているデバイスが"
								+ "このデバイスを連動して開いていないか確認してください。\n\n"
								+ e.getMessage();
						JOptionPane.showMessageDialog(
							null, message,
							"Cannot open MIDI device",
							JOptionPane.ERROR_MESSAGE
						);
						return;
					}
					if( deviceModel.getMidiDevice().isOpen() ) {
						dtde.dropComplete(true);
						//
						// デバイスが正常に開かれたことを確認できたら
						// ドロップした場所へフレームを配置して可視化する。
						//
						JInternalFrame frame = getFrameOf(deviceModel);
						if( frame != null ) {
							Point loc = dtde.getLocation();
							loc.translate( -frame.getWidth()/2, 0 );
							frame.setLocation(loc);
							frame.setVisible(true);
						}
						return;
					}
				}
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
		dtde.dropComplete(false);
	}
	/**
	 * 指定されたMIDIデバイスモデルに対するMIDIデバイスフレームを返します。
	 *
	 * @param deviceModel MIDIデバイスモデル
	 * @return 対応するMIDIデバイスフレーム（ない場合 null）
	 */
	public MidiDeviceFrame getFrameOf(MidiConnecterListModel deviceModel) {
		JInternalFrame[] frames = getAllFramesInLayer(JLayeredPane.DEFAULT_LAYER);
		for( JInternalFrame frame : frames ) {
			if( ! (frame instanceof MidiDeviceFrame) )
				continue;
			MidiDeviceFrame deviceFrame = (MidiDeviceFrame)frame;
			if( deviceFrame.listView.getModel() == deviceModel )
				return deviceFrame;
		}
		return null;
	}
	private boolean isTimerStarted;
	/**
	 * タイムスタンプを更新するタイマーを開始または停止します。
	 * @param toStart trueで開始、falseで停止
	 */
	public void setAllDeviceTimestampTimers(boolean toStart) {
		if( isTimerStarted == toStart ) return;
		isTimerStarted = toStart;
		JInternalFrame[] frames = getAllFramesInLayer(JLayeredPane.DEFAULT_LAYER);
		for( JInternalFrame frame : frames ) {
			if( ! (frame instanceof MidiDeviceFrame) )
				continue;
			MidiDeviceFrame deviceFrame = (MidiDeviceFrame)frame;
			Timer timer = deviceFrame.timer;
			if( toStart ) timer.start(); else timer.stop();
		}
	}
}

/**
 * MIDI ケーブル描画面
 */
class MidiCablePane extends JComponent
	implements ListDataListener, ComponentListener, InternalFrameListener
{
	private JDesktopPane desktopPane;
	//private JTree tree;
	public MidiCablePane(JDesktopPane desktopPane) {
		this.desktopPane = desktopPane;
		setOpaque(false);
		setVisible(true);
	}
	//
	// MidiDeviceFrame の開閉を検出
	public void internalFrameActivated(InternalFrameEvent e) {}
	public void internalFrameClosed(InternalFrameEvent e) { repaint(); }
	public void internalFrameClosing(InternalFrameEvent e) {
		JInternalFrame frame = e.getInternalFrame();
		if( ! (frame instanceof MidiDeviceFrame) )
			return;
		MidiDeviceFrame devFrame = (MidiDeviceFrame)frame;
		MidiConnecterListModel devModel = devFrame.listView.getModel();
		if( ! devModel.rxSupported() )
			return;
		colorMap.remove(devModel.getMidiDevice().getReceivers().get(0));
		repaint();
	}
	public void internalFrameDeactivated(InternalFrameEvent e) { repaint(); }
	public void internalFrameDeiconified(InternalFrameEvent e) {}
	public void internalFrameIconified(InternalFrameEvent e) {}
	public void internalFrameOpened(InternalFrameEvent e) {}
	//
	// ウィンドウオペレーションの検出
	public void componentHidden(ComponentEvent e) {}
	public void componentMoved(ComponentEvent e) { repaint(); }
	public void componentResized(ComponentEvent e) { repaint(); }
	public void componentShown(ComponentEvent e) {}
	//
	// MidiConnecterListModel における Transmitter リストの更新を検出
	public void contentsChanged(ListDataEvent e) { repaint(); }
	public void intervalAdded(ListDataEvent e) { repaint(); }
	public void intervalRemoved(ListDataEvent e) { repaint(); }
	//
	// ケーブル描画用
	private static final Stroke CABLE_STROKE = new BasicStroke(
		3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND
	);
	private static final Color[] CABLE_COLORS = {
		new Color(255, 0, 0,144),
		new Color(0, 255, 0,144),
		new Color(0, 0, 255,144),
		new Color(191,191,0,144),
		new Color(0,191,191,144),
		new Color(191,0,191,144),
	};
	private int nextColorIndex = 0;
	private Hashtable<Receiver,Color> colorMap = new Hashtable<>();
	@Override
	public void paint(Graphics g) {
		super.paint(g);
		Graphics2D g2 = (Graphics2D)g;
		g2.setStroke(CABLE_STROKE);
		JInternalFrame[] frames =
			desktopPane.getAllFramesInLayer(JLayeredPane.DEFAULT_LAYER);
		for( JInternalFrame frame : frames ) {
			if( ! (frame instanceof MidiDeviceFrame) )
				continue;
			MidiDeviceFrame txDeviceFrame = (MidiDeviceFrame)frame;
			List<Transmitter> txList = txDeviceFrame.listView.getModel().getMidiDevice().getTransmitters();
			for( Transmitter tx : txList ) {
				//
				// 送信端子から接続されている受信端子の存在を確認
				Receiver rx = tx.getReceiver();
				if( rx == null )
					continue;
				//
				// 送信端子の矩形を特定
				Rectangle txRect = txDeviceFrame.getListCellBounds(tx);
				if( txRect == null )
					continue;
				//
				// 受信端子のあるMIDIデバイスを探す
				Rectangle rxRect = null;
				for( JInternalFrame anotherFrame : frames ) {
					if( ! (anotherFrame instanceof MidiDeviceFrame) )
						continue;
					//
					// 受信端子の矩形を探す
					MidiDeviceFrame rxDeviceFrame = (MidiDeviceFrame)anotherFrame;
					if((rxRect = rxDeviceFrame.getListCellBounds(rx)) == null)
						continue;
					rxRect.translate(rxDeviceFrame.getX(), rxDeviceFrame.getY());
					break;
				}
				if( rxRect == null )
					continue;
				txRect.translate(txDeviceFrame.getX(), txDeviceFrame.getY());
				//
				// 色を探す
				Color color = colorMap.get(rx);
				if( color == null ) {
					colorMap.put(rx, color=CABLE_COLORS[nextColorIndex++]);
					if( nextColorIndex >= CABLE_COLORS.length )
						nextColorIndex = 0;
				}
				g2.setColor(color);
				//
				// Tx 始点
				int fromX = txRect.x;
				int fromY = txRect.y + 2;
				int d = txRect.height - 5;
				g2.fillOval(fromX, fromY, d, d);
				//
				// Tx → Rx 線
				int r = d / 2;
				fromX += r;
				fromY += r;
				d = rxRect.height - 5;
				r = d / 2;
				int toX = rxRect.x + r;
				int toY = rxRect.y + r + 2;
				g2.drawLine(fromX, fromY, toX, toY);
			}
		}
	}
}

