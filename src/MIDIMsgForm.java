import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.swing.Action;
import javax.swing.BoundedRangeModel;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * MIDI Message Form for MIDI Chord Helper
 *
 * @auther
 *	Copyright (C) 2006-2013 ＠きよし - Akiyoshi Kamide
 *	http://www.yk.rim.or.jp/~kamide/music/chordhelper/
 */
class MidiEventDialog extends JDialog {
	/**
	 * tick位置入力フォーム
	 */
	class TickPositionInputForm extends JPanel {
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
	TickPositionInputForm tickPositionInputForm;
	/**
	 * MIDIメッセージ入力フォーム
	 */
	MidiMessageForm midiMessageForm;
	/**
	 * キャンセルボタン
	 */
	JButton cancelButton;
	/**
	 * OKボタン（アクションによってラベルがOK以外に変わることがある）
	 */
	JButton okButton;
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
	public void openForm(String title, Action okAction, int midiChannel,
		boolean useTick, boolean useMessage ) {
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

/**
 * MIDI Message Entry Form - MIDIメッセージ入力欄
 */
class MidiMessageForm extends JPanel implements ActionListener {
	/**
	 * MIDIステータス
	 */
	DefaultComboBoxModel<String> statusComboBoxModel =
		new DefaultComboBoxModel<String>() {
			{
				int i; String s;
				// チャンネルメッセージ
				for( i = 0x80; i <= 0xE0 ; i += 0x10 ) {
					if( (s = MIDISpec.getStatusName(i)) == null )
						continue;
					addElement(String.format("0x%02X : %s", i, s));
				}
				// チャンネルを持たない SysEx やメタメッセージなど
				for( i = 0xF0; i <= 0xFF ; i++ ) {
					if( (s = MIDISpec.getStatusName(i)) == null )
						continue;
					addElement(String.format("0x%02X : %s", i, s));
				}
			}
		};
	/**
	 * ノート番号
	 */
	DefaultComboBoxModel<String> noteComboBoxModel =
		new DefaultComboBoxModel<String>() {
			{
				for( int i = 0; i<=0x7F; i++ ) addElement(
					String.format(
						"0x%02X : %d : %s", i, i, Music.NoteSymbol.noteNoToSymbol(i)
					)
				);
				// Center note C
				setSelectedItem(getElementAt(60));
			}
		};
	/**
	 * 打楽器名
	 */
	DefaultComboBoxModel<String> percussionComboBoxModel =
		new DefaultComboBoxModel<String>() {
			{
				for( int i = 0; i<=0x7F; i++ ) addElement(
					String.format(
						"0x%02X : %d : %s", i, i, MIDISpec.getPercussionName(i)
					)
				);
				setSelectedItem(getElementAt(35)); // Acoustic Bass Drum
			}
		};
	/**
	 * コントロールチェンジ
	 */
	DefaultComboBoxModel<String> controlChangeComboBoxModel =
		new DefaultComboBoxModel<String>() {
			{
				String s;
				for( int i = 0; i<=0x7F; i++ ) {
					if( (s = MIDISpec.getControllerName(i)) == null )
						continue;
					addElement(String.format("0x%02X : %d : %s", i, i, s));
				}
			}
		};
	/**
	 * 楽器名（音色）
	 */
	DefaultComboBoxModel<String> instrumentComboBoxModel =
		new DefaultComboBoxModel<String>() {
			{
				for( int i = 0; i<=0x7F; i++ ) addElement(
					String.format(
						"0x%02X : %s", i, MIDISpec.instrument_names[i]
					)
				);
			}
		};
	/**
	 * MetaMessage Type
	 */
	DefaultComboBoxModel<String> metaTypeComboBoxModel =
		new DefaultComboBoxModel<String>() {
			{
				String s;
				String initial_type_string = null;
				for( int type = 0; type < 0x80 ; type++ ) {
					if( (s = MIDISpec.getMetaName(type)) == null ) {
						continue;
					}
					s = String.format("0x%02X : %s", type, s);
					addElement(s);
					if( type == 0x51 )
						initial_type_string = s; // Tempo
				}
				setSelectedItem(initial_type_string);
			}
		};
	/**
	 * １６進数値のみの選択データモデル
	 */
	DefaultComboBoxModel<String> hexData1ComboBoxModel =
		new DefaultComboBoxModel<String>() {
			{
				for( int i = 0; i<=0x7F; i++ ) {
					addElement(String.format("0x%02X : %d", i, i ));
				}
			}
		};
	/**
	 * １６進数値のみの選択データモデル（ShortMessageデータ２バイト目）
	 */
	DefaultComboBoxModel<String> hexData2ComboBoxModel =
		new DefaultComboBoxModel<String>() {
			{
				for( int i = 0; i<=0x7F; i++ ) {
					addElement(String.format("0x%02X : %d", i, i ));
				}
			}
		};
	// データ選択操作部
	private HexSelecter statusText = new HexSelecter("Status/Command");
	private HexSelecter data1Text = new HexSelecter("[Data1] ");
	private HexSelecter data2Text = new HexSelecter("[Data2] ");
	MidiChannelComboSelecter channelText =
		new MidiChannelComboSelecter("MIDI Channel");

	private JComboBox<String> statusComboBox = statusText.getComboBox();
	private JComboBox<String> data1ComboBox = data1Text.getComboBox();
	private JComboBox<String> data2ComboBox = data2Text.getComboBox();
	private JComboBox<Integer> channelComboBox = channelText.getComboBox();

	/**
	 * 長い値（テキストまたは数値）の入力欄
	 */
	private HexTextForm dataText = new HexTextForm("Data:",3,50);
	/**
	 * 音階入力用ピアノキーボード
	 */
	private PianoKeyboardPanel keyboardPanel = new PianoKeyboardPanel() {
		{
			keyboard.setPreferredSize(new Dimension(300,40));
			keyboard.addPianoKeyboardListener(
				new PianoKeyboardAdapter() {
					public void pianoKeyPressed(int n, InputEvent e) {
						data1Text.setValue(n);
						if( midiChannels != null )
							midiChannels[channelText.getSelectedChannel()].
							noteOn( n, data2Text.getValue() );
					}
					public void pianoKeyReleased(int n, InputEvent e) {
						if( midiChannels != null ) {
							midiChannels[channelText.getSelectedChannel()].
							noteOff( n, data2Text.getValue() );
						}
					}
				}
			);
		}
	};
	/**
	 * 音の長さ
	 */
	DurationForm durationForm = new DurationForm();

	// メタイベント
	/**
	 * テンポ選択
	 */
	TempoSelecter tempoSelecter = new TempoSelecter() {
		{
			tempoSpinnerModel.addChangeListener(
				new ChangeListener() {
					@Override
					public void stateChanged(ChangeEvent e) {
						dataText.setValue(getTempoByteArray());
					}
				}
			);
		}
	};
	/**
	 * 拍子選択
	 */
	TimeSignatureSelecter timesigSelecter = new TimeSignatureSelecter() {
		{
			upperTimesigSpinnerModel.addChangeListener(
				new ChangeListener() {
					@Override
					public void stateChanged(ChangeEvent e) {
						dataText.setValue(getByteArray());
					}
				}
			);
			lowerTimesigCombobox.addActionListener(
				new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						dataText.setValue(getByteArray());
					}
				}
			);
		}
	};
	/**
	 * 調号選択
	 */
	KeySignatureSelecter keysigSelecter = new KeySignatureSelecter() {
		{
			keysigCombobox.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						dataText.setValue(getKey().getBytes());
					}
				}
			);
			minorCheckbox.addItemListener(
				new ItemListener() {
					public void itemStateChanged(ItemEvent e) {
						dataText.setValue(getKey().getBytes());
					}
				}
			);
		}
	};

	/**
	 * 音を鳴らす出力MIDIチャンネル
	 */
	private MidiChannel[] midiChannels = null;

	/**
	 * Note on/off のときに Duration フォームを表示するか
	 */
	private boolean isDurationVisible = true;

	public MidiMessageForm() {
		statusComboBox.setModel(statusComboBoxModel);
		statusComboBox.setSelectedIndex(1); // NoteOn
		data2ComboBox.setModel(hexData2ComboBoxModel);
		data2ComboBox.setSelectedIndex(64); // Center
		statusComboBox.addActionListener(this);
		channelComboBox.addActionListener(this);
		data1ComboBox.addActionListener(this);
		setLayout(new BoxLayout( this, BoxLayout.Y_AXIS ));
		add(new JPanel() {{ add(statusText); add(channelText); }});
		add(durationForm);
		add(new JPanel() {{ add(data1Text); add(keyboardPanel); }});
		add(new JPanel() {{ add(data2Text); }});
		add( tempoSelecter );
		add( timesigSelecter );
		add( keysigSelecter );
		add( dataText );
		updateVisible();
	}
	@Override
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		if( src == data1ComboBox ) {
			int status = statusText.getValue();
			int data1 = data1Text.getValue();
			if( isNote(status) ) { // Data1 -> Note
				if( data1 >= 0 ) keyboardPanel.keyboard.setSelectedNote(data1);
			}
			else if( status == 0xFF ) {
				switch( data1 ) { // Data type -> Selecter
				case 0x51: dataText.setValue(tempoSelecter.getTempoByteArray()); break;
				case 0x58: dataText.setValue(timesigSelecter.getByteArray()); break;
				case 0x59: dataText.setValue(keysigSelecter.getKey().getBytes()); break;
				default: break;
				}
			}
		}
		updateVisible();
	}
	/**
	 * このMIDIメッセージフォームにMIDIチャンネルを設定します。
	 *
	 * <p>設定したMIDIチャンネルには、
	 * ダイアログ内のピアノキーボードで音階を入力したときに
	 * ノートON/OFFが出力されます。これにより実際に音として聞けるようになります。
	 * </p>
	 *
	 * @param midiChannels MIDIチャンネル
	 */
	public void setOutputMidiChannels( MidiChannel midiChannels[] ) {
		this.midiChannels = midiChannels;
	}
	/**
	 * 時間間隔入力の表示状態を変更します。
	 * @param isVisible trueで表示、falseで非表示
	 */
	public void setDurationVisible(boolean isVisible) {
		isDurationVisible = isVisible;
		updateVisible();
	}
	/**
	 * 時間間隔入力の表示状態を返します。
	 * @return true：表示中 false：非表示中
	 */
	public boolean isDurationVisible() {
		return isDurationVisible;
	}
	/**
	 * 各入力欄の表示状態を更新します。
	 */
	public void updateVisible() {
		int msgStatus = statusText.getValue();
		boolean is_ch_msg = MIDISpec.isChannelMessage(msgStatus);
		channelText.setVisible(is_ch_msg);
		statusText.setTitle("[Status] "+(is_ch_msg ? "Command" : ""));
		durationForm.setVisible( isDurationVisible && isNote(msgStatus) );
		keyboardPanel.setVisible( msgStatus <= 0xAF );
		data1Text.setVisible(
			msgStatus <= 0xEF ||
			msgStatus >= 0xF1 && msgStatus <= 0xF3 ||
			msgStatus == 0xFF
		);
		data2Text.setVisible(
			!(
				(msgStatus >= 0xC0 && msgStatus <= 0xDF)
				||
				msgStatus == 0xF0 || msgStatus == 0xF1
				||
				msgStatus == 0xF3 || msgStatus >= 0xF6
			)
		);
		data2Text.setTitle("[Data2] "+(
			msgStatus <= 0x9F ? "Velocity" :
			msgStatus <= 0xAF ? "Pressure" :
			msgStatus <= 0xBF ? "Value" :
			(msgStatus & 0xF0) == 0xE0 ? "High 7bit value" : ""
		));
		// Show if Sysex or Meta
		dataText.setVisible(
			msgStatus == 0xF0 || msgStatus == 0xF7 || msgStatus == 0xFF
		);
		if( msgStatus != 0xFF ) {
			tempoSelecter.setVisible(false);
			timesigSelecter.setVisible(false);
			keysigSelecter.setVisible(false);
		}
		switch( msgStatus & 0xF0 ) {
		// ステータスに応じて、１バイト目のデータモデルを切り替える。

		case 0x80: // Note Off
		case 0x90: // Note On
		case 0xA0: // Polyphonic Key Pressure
			int ch = channelText.getSelectedChannel();
			data1Text.setTitle("[Data1] "+(ch == 9 ? "Percussion" : "Note No."));
			data1ComboBox.setModel(ch == 9 ? percussionComboBoxModel : noteComboBoxModel);
			break;

		case 0xB0: // Control Change / Mode Change
			data1Text.setTitle("[Data1] Control/Mode No.");
			data1ComboBox.setModel(controlChangeComboBoxModel);
			break;

		case 0xC0: // Program Change
			data1Text.setTitle( "[Data1] Program No.");
			data1ComboBox.setModel(instrumentComboBoxModel);
			break;

		case 0xD0: // Channel Pressure
			data1Text.setTitle("[Data1] Pressure");
			data1ComboBox.setModel(hexData1ComboBoxModel);
			break;

		case 0xE0: // Pitch Bend
			data1Text.setTitle("[Data1] Low 7bit value");
			data1ComboBox.setModel(hexData1ComboBoxModel);
			break;

		default:
			if( msgStatus == 0xFF ) { // MetaMessage
				data1Text.setTitle("[Data1] MetaEvent Type");
				data1ComboBox.setModel(metaTypeComboBoxModel);
				int msgType = data1Text.getValue();
				tempoSelecter.setVisible( msgType == 0x51 );
				timesigSelecter.setVisible( msgType == 0x58 );
				keysigSelecter.setVisible( msgType == 0x59 );
				//
				if( MIDISpec.isEOT(msgType) ) {
					dataText.clear();
					dataText.setVisible(false);
				}
				else {
					dataText.setTitle(MIDISpec.hasMetaText(msgType)?"Text:":"Data:");
				}
			}
			else {
				data1Text.setTitle("[Data1] ");
				data1ComboBox.setModel(hexData1ComboBoxModel);
			}
			break;
		}
	}
	/**
	 * 入力している内容からMIDIメッセージを生成して返します。
	 * @return 入力している内容から生成したMIDIメッセージ
	 */
	public MidiMessage getMessage() {
		int msg_status = statusText.getValue();
		if( msg_status < 0 ) {
			return null;
		}
		else if( msg_status == 0xFF ) {
			int msg_type = data1Text.getValue();
			if( msg_type < 0 ) return null;
			byte msg_data[];
			if( MIDISpec.hasMetaText( msg_type ) ) {
				msg_data = dataText.getBytesFromString();
			}
			else if( msg_type == 0x2F ) { // EOT
				// To avoid inserting un-removable EOT, ignore the data.
				msg_data = new byte[0];
			}
			else {
				if( (msg_data = dataText.getBytes() ) == null ) {
					return null;
				}
			}
			MetaMessage msg = new MetaMessage();
			try {
				msg.setMessage( msg_type, msg_data, msg_data.length );
			} catch( InvalidMidiDataException e ) {
				e.printStackTrace();
				return null;
			}
			return (MidiMessage)msg;
		}
		else if( msg_status == 0xF0 || msg_status == 0xF7 ) {
			SysexMessage msg = new SysexMessage();
			byte data[] = dataText.getBytes();
			if( data == null ) return null;
			try {
				msg.setMessage(
						(int)(msg_status & 0xFF), data, data.length
						);
			} catch( InvalidMidiDataException e ) {
				e.printStackTrace();
				return null;
			}
			return (MidiMessage)msg;
		}
		ShortMessage msg = new ShortMessage();
		int msg_data1 = data1Text.getValue();
		if( msg_data1 < 0 ) msg_data1 = 0;
		int msg_data2 = data2Text.getValue();
		if( msg_data2 < 0 ) msg_data2 = 0;
		try {
			if( MIDISpec.isChannelMessage( msg_status ) ) {
				msg.setMessage(
					(msg_status & 0xF0),
					channelText.getSelectedChannel(),
					msg_data1, msg_data2
				);
			}
			else {
				msg.setMessage( msg_status, msg_data1, msg_data2 );
			}
		} catch( InvalidMidiDataException e ) {
			e.printStackTrace();
			return null;
		}
		return (MidiMessage)msg;
	}
	/**
	 * MIDIメッセージを入力欄に反映します。
	 * @param msg MIDIメッセージ
	 */
	public void setMessage( MidiMessage msg ) {
		if( msg instanceof ShortMessage ) {
			ShortMessage smsg = (ShortMessage)msg;
			int msg_ch = 0;
			int msg_status = smsg.getStatus();
			if( MIDISpec.isChannelMessage(msg_status) ) {
				msg_status = smsg.getCommand();
				msg_ch = smsg.getChannel();
			}
			statusText.setValue( msg_status );
			channelText.setSelectedChannel( msg_ch );
			data1Text.setValue( smsg.getData1() );
			data2Text.setValue( smsg.getData2() );
		}
		else if( msg instanceof SysexMessage ) {
			SysexMessage sysex_msg = (SysexMessage)msg;
			statusText.setValue( sysex_msg.getStatus() );
			dataText.setValue( sysex_msg.getData() );
		}
		else if( msg instanceof MetaMessage ) {
			MetaMessage meta_msg = (MetaMessage)msg;
			int msg_type = meta_msg.getType();
			byte data[] = meta_msg.getData();
			statusText.setValue( 0xFF );
			data1Text.setValue( msg_type );
			switch( msg_type ) {
			case 0x51: tempoSelecter.setTempo( data ); break;
			case 0x58: timesigSelecter.setValue( data[0], data[1] ); break;
			case 0x59: keysigSelecter.setKey( new Music.Key(data) ); break;
			default: break;
			}
			if( MIDISpec.hasMetaText( msg_type ) ) {
				dataText.setString( new String(data) );
			}
			else {
				dataText.setValue( data );
			}
			updateVisible();
		}
	}
	/**
	 * ノートメッセージを設定します。
	 * @param channel MIDIチャンネル
	 * @param noteNumber ノート番号
	 * @param velocity ベロシティ
	 * @return 常にtrue
	 */
	public boolean setNote(int channel, int noteNumber, int velocity) {
		channelText.setSelectedChannel(channel);
		data1Text.setValue(noteNumber);
		data2Text.setValue(velocity);
		return true;
	}
	/**
	 * 入力内容がノートメッセージかどうか調べます。
	 * @return ノートメッセージのときtrue
	 */
	public boolean isNote() {
		return isNote(statusText.getValue());
	}
	/**
	 * 入力内容がノートメッセージかどうか調べます。
	 * @param status MIDIメッセージのステータス
	 * @return ノートメッセージのときtrue
	 */
	public boolean isNote(int status) {
		int cmd = status & 0xF0;
		return (cmd == ShortMessage.NOTE_ON || cmd == ShortMessage.NOTE_OFF);
	}
	/**
	 * 入力内容が NoteOn または NoteOff かどうか調べます。
	 * @param isNoteOn NoteOnを調べるときtrue、NoteOffを調べるときfalse
	 * @return 該当するときtrue
	 */
	public boolean isNote(boolean isNoteOn) {
		int status = statusText.getValue();
		int cmd = status & 0xF0;
		return (
			isNoteOn && cmd == ShortMessage.NOTE_ON && data2Text.getValue() > 0
			||
			!isNoteOn && (
				cmd == ShortMessage.NOTE_ON && data2Text.getValue() <= 0 ||
				cmd == ShortMessage.NOTE_OFF
			)
		);
	}
	/**
	 * 入力されたMIDIメッセージがNoteOn/NoteOffのときに、
	 * そのパートナーとなるNoteOff/NoteOnメッセージを生成します。
	 *
	 * @return パートナーメッセージ（ない場合null）
	 */
	public ShortMessage createPartnerMessage() {
		ShortMessage sm = (ShortMessage)getMessage();
		if( sm == null ) return null;
		ShortMessage partnerSm;
		if( isNote(true) ) { // NoteOn
			partnerSm = new ShortMessage();
			try{
				partnerSm.setMessage(
					ShortMessage.NOTE_OFF,
					sm.getChannel(), sm.getData1(), sm.getData2()
				);
			} catch( InvalidMidiDataException e ) {
				e.printStackTrace();
				return null;
			}
			return partnerSm;
		}
		else if( isNote(false) ) { // NoteOff
			partnerSm = new ShortMessage();
			try{
				partnerSm.setMessage(
					ShortMessage.NOTE_ON,
					sm.getChannel(),
					sm.getData1() == 0 ? 100 : sm.getData1(),
					sm.getData2()
				);
			} catch( InvalidMidiDataException e ) {
				e.printStackTrace();
				return null;
			}
			return partnerSm;
		}
		return null;
	}
}


/**
 * 16進数テキスト入力フォーム [0x00 0x00 0x00 ... ]
 */
class HexTextForm extends JPanel {
	public JTextArea textArea;
	public JLabel titleLabel;
	public HexTextForm(String title) {
		this(title,1,3);
	}
	public HexTextForm(String title, int rows, int columns) {
		if( title != null )
			add(titleLabel = new JLabel(title));
		textArea = new JTextArea(rows, columns) {{
			setLineWrap(true);
		}};
		add(new JScrollPane(textArea));
		setLayout(new FlowLayout());
	}
	public String getString() {
		return textArea.getText();
	}
	public byte[] getBytesFromString() {
		return getString().getBytes();
	}
	public byte[] getBytes() {
		String words[] = getString().trim().split(" +");
		ArrayList<Integer> tmp_ba = new ArrayList<Integer>();
		int i;
		for( String w : words ) {
			if( w.length() == 0 ) continue;
			try {
				i = Integer.decode(w).intValue();
			} catch( NumberFormatException e ) {
				JOptionPane.showMessageDialog(
						this,
						w + " : is not a number",
						"MIDI Chord Helper",
						JOptionPane.ERROR_MESSAGE
						);
				return null;
			}
			tmp_ba.add(i);
		}
		byte[] ba = new byte[tmp_ba.size()];
		i = 0;
		for( Integer b : tmp_ba ) {
			ba[i++] = (byte)( b.intValue() & 0xFF );
		}
		return ba;
	}
	public void setTitle( String str ) {
		titleLabel.setText( str );
	}
	public void setString( String str ) {
		textArea.setText( str );
	}
	public void setValue( int val ) {
		textArea.setText( String.format( " 0x%02X", val ) );
	}
	public void setValue( byte val ) {
		textArea.setText( String.format( " 0x%02X", val ) );
	}
	public void setValue( byte ba[] ) {
		String str = "";
		for( byte b : ba ) {
			str += String.format( " 0x%02X", b );
		}
		textArea.setText(str);
	}
	public void clear() { textArea.setText(""); }
}
/**
 * 16進数選択 [0x00 0x00 0x00 ... ] v -> Select
 */
class HexSelecter extends JPanel {
	private JComboBox<String> comboBox = new JComboBox<String>() {{
		setEditable(true);
		setMaximumRowCount(16);
	}};
	private JLabel title;
	public HexSelecter( String title ) {
		if( title != null )
			add( this.title = new JLabel(title) );
		add(comboBox);
		setLayout(new FlowLayout());
	}
	public JComboBox<String> getComboBox() { return comboBox; }
	public void setTitle(String title) { this.title.setText(title); }
	public int getValue() {
		ArrayList<Integer> ia = getIntegerList();
		return ia.size() == 0 ? -1 : ia.get(0);
	}
	public ArrayList<Integer> getIntegerList() {
		String words[];
		String str = (String)(comboBox.getSelectedItem());
		if( str == null )
			words = new String[0];
		else
			words = str.replaceAll( ":.*$", "" ).trim().split(" +");
		int i;
		ArrayList<Integer> ia = new ArrayList<Integer>();
		for( String w : words ) {
			if( w.length() == 0 ) continue;
			try {
				i = Integer.decode(w).intValue();
			} catch( NumberFormatException e ) {
				JOptionPane.showMessageDialog(
					this,
					w + " : is not a number",
					"MIDI Chord Helper",
					JOptionPane.ERROR_MESSAGE
				);
				return null;
			}
			ia.add(i);
		}
		return ia;
	}
	public byte[] getBytes() {
		ArrayList<Integer> ia = getIntegerList();
		byte[] ba = new byte[ia.size()];
		int i = 0;
		for( Integer ib : ia ) {
			ba[i++] = (byte)( ib.intValue() & 0xFF );
		}
		return ba;
	}
	public void setValue( int val ) {
		setValue( (byte)(val & 0xFF) );
	}
	public void setValue( byte val ) {
		int n_item = comboBox.getItemCount();
		String item;
		for( int i=0; i<n_item; i++ ) {
			item = (String)( comboBox.getItemAt(i) );
			if( Integer.decode( item.trim().split(" +")[0] ).byteValue() == val ) {
				comboBox.setSelectedIndex(i);
				return;
			}
		}
		comboBox.setSelectedItem(String.format(" 0x%02X",val));
	}
	public void setValue( byte ba[] ) {
		String str = "";
		for( byte b : ba )
			str += String.format( " 0x%02X", b );
		comboBox.setSelectedItem(str);
	}
	public void clear() {
		comboBox.setSelectedItem("");
	}
}

/**
 * MIDIチャンネル選択コンボボックスモデル
 */
interface MidiChannelComboBoxModel extends ComboBoxModel<Integer> {
	int getSelectedChannel();
	void setSelectedChannel(int channel);
}
/**
 * MIDIチャンネル選択コンボボックスモデルのデフォルト実装
 */
class DefaultMidiChannelComboBoxModel extends DefaultComboBoxModel<Integer>
	implements MidiChannelComboBoxModel
{
	public DefaultMidiChannelComboBoxModel() {
		for( int ch = 0; ch < MIDISpec.MAX_CHANNELS ; ch++ )
			addElement(ch+1);
	}
	public int getSelectedChannel() {
		return getIndexOf(getSelectedItem());
	}
	public void setSelectedChannel(int channel) {
		setSelectedItem(getElementAt(channel));
	}
}
/**
 * MIDIチャンネル選択ビュー（コンボボックス）
 */
class MidiChannelComboSelecter extends JPanel {
	JComboBox<Integer> comboBox = new JComboBox<>();
	public MidiChannelComboSelecter( String title ) {
		this(title, new DefaultMidiChannelComboBoxModel());
	}
	public MidiChannelComboSelecter(String title, MidiChannelComboBoxModel model) {
		setLayout(new FlowLayout());
		if( title != null ) add( new JLabel(title) );
		comboBox.setModel(model);
		comboBox.setMaximumRowCount(16);
		add(comboBox);
	}
	public JComboBox<Integer> getComboBox() {
		return comboBox;
	}
	public MidiChannelComboBoxModel getModel() {
		return (MidiChannelComboBoxModel)comboBox.getModel();
	}
	public int getSelectedChannel() {
		return comboBox.getSelectedIndex();
	}
	public void setSelectedChannel(int channel) {
		comboBox.setSelectedIndex(channel);
	}
}
/**
 * MIDIチャンネル選択ビュー（リストボタン）
 */
class MidiChannelButtonSelecter extends JList<Integer>
	implements ListDataListener, ListSelectionListener
{
	private PianoKeyboard keyboard = null;
	public MidiChannelButtonSelecter(MidiChannelComboBoxModel model) {
		super(model);
		setLayoutOrientation(HORIZONTAL_WRAP);
		setVisibleRowCount(1);
		setCellRenderer(new MyCellRenderer());
		setSelectedIndex(model.getSelectedChannel());
		model.addListDataListener(this);
		addListSelectionListener(this);
	}
	@Override
	public void contentsChanged(ListDataEvent e) {
		setSelectedIndex(getModel().getSelectedChannel());
	}
	@Override
	public void intervalAdded(ListDataEvent e) {}
	@Override
	public void intervalRemoved(ListDataEvent e) {}
	@Override
	public void valueChanged(ListSelectionEvent e) {
		getModel().setSelectedChannel(getSelectedIndex());
	}
	public MidiChannelButtonSelecter(PianoKeyboard keyboard) {
		this(keyboard.midiChComboboxModel);
		setPianoKeyboard(keyboard);
	}
	@Override
	public MidiChannelComboBoxModel getModel() {
		return (MidiChannelComboBoxModel)(super.getModel());
	}
	public void setPianoKeyboard(PianoKeyboard keyboard) {
		(this.keyboard = keyboard).midi_ch_button_selecter = this;
	}
	class MyCellRenderer extends JLabel implements ListCellRenderer<Integer> {
		private boolean cellHasFocus = false;
		public MyCellRenderer() {
			setOpaque(true);
			setHorizontalAlignment(CENTER);
			setSelectionBackground(Color.yellow);
		}
		@Override
		public Component getListCellRendererComponent(
			JList<? extends Integer> list,
			Integer value, int index,
			boolean isSelected, boolean cellHasFocus
		) {
			this.cellHasFocus = cellHasFocus;
			setText(value.toString());
			if(isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			} else {
				setBackground(
					keyboard != null && keyboard.countKeyOn(index) > 0 ?
					Color.pink : list.getBackground()
				);
				setForeground(list.getForeground());
			}
			setEnabled(list.isEnabled());
			setFont(list.getFont());
			return this;
		}
		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			if( cellHasFocus ) {
				g.setColor(Color.gray);
				g.drawRect(0, 0, this.getWidth() - 1, this.getHeight() - 1);
			}
		}
	}
}

/**
 * tick位置入力モデル Mesausre:[xxxx] Beat:[xx] ExTick:[xxx]
 */
class TickPositionModel implements ChangeListener {
	public SpinnerNumberModel tickModel = new SpinnerNumberModel(0L, 0L, 999999L, 1L);
	public SpinnerNumberModel measureModel = new SpinnerNumberModel(1, 1, 9999, 1);
	public SpinnerNumberModel beatModel = new SpinnerNumberModel(1, 1, 32, 1);
	public SpinnerNumberModel extraTickModel = new SpinnerNumberModel(0, 0, 4*960-1, 1);
	/**
	 * 新しい {@link TickPositionModel} を構築します。
	 */
	public TickPositionModel() {
		tickModel.addChangeListener(this);
		measureModel.addChangeListener(this);
		beatModel.addChangeListener(this);
		extraTickModel.addChangeListener(this);
	}
	private SequenceTickIndex sequenceTickIndex;
	private boolean isChanging = false;
	@Override
	public void stateChanged(ChangeEvent e) {
		if( sequenceTickIndex == null )
			return;
		if( e.getSource() == tickModel ) {
			isChanging = true;
			long newTick = tickModel.getNumber().longValue();
			int newMeasure = 1 + sequenceTickIndex.tickToMeasure(newTick);
			measureModel.setValue(newMeasure);
			beatModel.setValue(sequenceTickIndex.lastBeat + 1);
			isChanging = false;
			extraTickModel.setValue(sequenceTickIndex.lastExtraTick);
			return;
		}
		if( isChanging )
			return;
		long newTick = sequenceTickIndex.measureToTick(
			measureModel.getNumber().intValue() - 1,
			beatModel.getNumber().intValue() - 1,
			extraTickModel.getNumber().intValue()
		);
		tickModel.setValue(newTick);
	}
	public void setSequenceIndex(SequenceTickIndex sequenceTickIndex) {
		this.sequenceTickIndex = sequenceTickIndex;
		extraTickModel.setMaximum( 4 * sequenceTickIndex.getResolution() - 1 );
	}
	public long getTickPosition() {
		return tickModel.getNumber().longValue();
	}
	public void setTickPosition( long tick ) {
		tickModel.setValue(tick);
	}
}


/**
 * 音長入力フォーム
 */
class DurationForm extends JPanel implements ActionListener, ChangeListener {
	class NoteIcon extends ButtonIcon {
		public NoteIcon( int kind ) { super(kind); }
		public int getDuration() {
			if(  ! isMusicalNote() ) return -1;
			int duration = (ppq * 4) >> getMusicalNoteValueIndex();
			if( isDottedMusicalNote() )
				duration += duration / 2;
			return duration;
		}
	}
	class NoteRenderer extends JLabel implements ListCellRenderer<NoteIcon> {
		public NoteRenderer() { setOpaque(true); }
		@Override
		public Component getListCellRendererComponent(
			JList<? extends NoteIcon> list,
			NoteIcon icon,
			int index,
			boolean isSelected,
			boolean cellHasFocus
		) {
			setIcon( icon );
			int duration = icon.getDuration();
			setText( duration < 0 ? null : ("" + duration) );
			setFont( list.getFont() );
			if (isSelected) {
				setBackground( list.getSelectionBackground() );
				setForeground( list.getSelectionForeground() );
			} else {
				setBackground( list.getBackground() );
				setForeground( list.getForeground() );
			}
			return this;
		}
	}
	class NoteComboBox extends JComboBox<NoteIcon> {
		public NoteComboBox() {
			setRenderer( new NoteRenderer() );
			addItem( new NoteIcon(ButtonIcon.EDIT_ICON) );
			addItem( new NoteIcon(ButtonIcon.WHOLE_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.DOTTED_HALF_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.HALF_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.DOTTED_QUARTER_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.QUARTER_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.DOTTED_8TH_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.A8TH_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.DOTTED_16TH_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.A16TH_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.DOTTED_32ND_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.A32ND_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.A64TH_NOTE_ICON) );
			setMaximumRowCount(16);
			setSelectedIndex(5);
		}
		public int getDuration() {
			NoteIcon icon = (NoteIcon)getSelectedItem();
			return icon==null ? -1 : icon.getDuration();
		}
		public void setDuration(int duration) {
			int n_items = getItemCount();
			for( int i = 1; i < n_items; i++ ) {
				NoteIcon icon = getItemAt(i);
				int icon_duration = icon.getDuration();
				if( icon_duration < 0 || icon_duration != duration )
					continue;
				setSelectedItem(icon);
				return;
			}
			setSelectedIndex(0);
		}
	}
	class DurationModel extends SpinnerNumberModel {
		public DurationModel() { super( ppq, 1, ppq*4*4, 1 ); }
		public void setDuration( int value ) {
			setValue( new Integer(value) );
		}
		public int getDuration() {
			return getNumber().intValue();
		}
		public void setPPQ( int ppq ) {
			setMaximum( ppq*4*4 );
			setDuration( ppq );
		}
	}
	DurationModel model;
	JSpinner spinner;
	NoteComboBox note_combo;
	JLabel title_label, unit_label;
	private int ppq = 960;
	//
	public DurationForm() {
		(model = new DurationModel()).addChangeListener(this);
		(note_combo = new NoteComboBox()).addActionListener(this);
		add( title_label = new JLabel("Duration:") );
		add( note_combo );
		add( spinner = new JSpinner( model ) );
		add( unit_label = new JLabel("[Ticks]") );
	}
	@Override
	public void actionPerformed(ActionEvent e) {
		int duration = note_combo.getDuration();
		if( duration < 0 ) return;
		model.setDuration( duration );
	}
	@Override
	public void stateChanged(ChangeEvent e) {
		note_combo.setDuration( model.getDuration() );
	}
	@Override
	public void setEnabled( boolean enabled ) {
		super.setEnabled(enabled);
		title_label.setEnabled(enabled);
		spinner.setEnabled(enabled);
		note_combo.setEnabled(enabled);
		unit_label.setEnabled(enabled);
	}
	public void setPPQ( int ppq ) {
		model.setPPQ(this.ppq = ppq);
	}
	public int getDuration() {
		return model.getDuration();
	}
	public void setDuration( int duration ) {
		model.setDuration(duration);
	}
}

/**
 * テンポ選択（QPM: Quarter Per Minute）
 */
class TempoSelecter extends JPanel implements MouseListener, MetaEventListener {
	static final int DEFAULT_QPM = 120;
	protected SpinnerNumberModel tempoSpinnerModel =
		new SpinnerNumberModel(DEFAULT_QPM, 1, 999, 1);
	private JLabel tempoLabel = new JLabel(
		"=", new ButtonIcon(ButtonIcon.QUARTER_NOTE_ICON), JLabel.CENTER
	) {{
		setVerticalAlignment(JLabel.CENTER);
	}};
	private JLabel tempoValueLabel = new JLabel(""+DEFAULT_QPM);
	private JSpinner tempoSpinner = new JSpinner(tempoSpinnerModel);
	public TempoSelecter() {
		String tooltip = "Tempo in quatrers per minute - テンポ（１分あたりの四分音符の数）";
		tempoSpinner.setToolTipText(tooltip);
		tempoValueLabel.setToolTipText(tooltip);
		setLayout(new BoxLayout(this,BoxLayout.X_AXIS));
		add(tempoLabel);
		add(Box.createHorizontalStrut(5));
		add(tempoSpinner);
		add(tempoValueLabel);
		setEditable(true);
		tempoLabel.addMouseListener(this);
	}
	private long prevBeatMicrosecondPosition = 0;
	private class SetTempoRunnable implements Runnable {
		byte[] qpm;
		public SetTempoRunnable(byte[] qpm) { this.qpm = qpm; }
		@Override
		public void run() { setTempo(qpm);}
	}
	@Override
	public void meta(MetaMessage msg) {
		switch(msg.getType()) {
		case 0x51: // Tempo (3 bytes) - テンポ
			if( ! SwingUtilities.isEventDispatchThread() ) {
				SwingUtilities.invokeLater(new SetTempoRunnable(msg.getData()));
				break;
			}
			setTempo(msg.getData());
			break;
		}
	}
	@Override
	public void mousePressed(MouseEvent e) {
		Component obj = e.getComponent();
		if(obj == tempoLabel && isEditable()) {
			//
			// Adjust tempo by interval time between two clicks
			//
			long currentMicrosecond = System.nanoTime()/1000;
			// midi_ch_selecter.noteOn( 9, 37, 100 );
			long interval_us = currentMicrosecond - prevBeatMicrosecondPosition;
			prevBeatMicrosecondPosition = currentMicrosecond;
			if( interval_us < 2000000L /* Shorter than 2 sec only */ ) {
				int tempo_in_bpm = (int)(240000000L / interval_us) >> 2; //  n/4拍子の場合のみを想定
			int old_tempo_in_bpm = getTempoInQpm();
			setTempo( ( tempo_in_bpm + old_tempo_in_bpm * 2 ) / 3 );
			}
		}
	}
	public void mouseReleased(MouseEvent e) { }
	public void mouseEntered(MouseEvent e) { }
	public void mouseExited(MouseEvent e) { }
	public void mouseClicked(MouseEvent e) { }
	private boolean	editable;
	/**
	 * 編集可能かどうかを返します。
	 * @return 編集可能ならtrue
	 */
	public boolean isEditable() { return editable; }
	/**
	 * 編集可能かどうかを設定します。
	 * @param editable 編集可能ならtrue
	 */
	public void setEditable( boolean editable ) {
		this.editable = editable;
		tempoSpinner.setVisible( editable );
		tempoValueLabel.setVisible( !editable );
		if( !editable ) {
			// Copy spinner's value to label
			tempoValueLabel.setText(
				""+tempoSpinnerModel.getNumber().intValue()
			);
		}
		tempoLabel.setToolTipText(
			editable ?
			"Click rhythmically to adjust tempo - ここをクリックしてリズムをとるとテンポを合わせられます"
			: null
		);
	}
	/**
	 * テンポを返します。
	 * @return テンポ [BPM](QPM)
	 */
	public int getTempoInQpm() {
		return tempoSpinnerModel.getNumber().intValue();
	}
	/**
	 * テンポをMIDIメタメッセージのバイト列として返します。
	 * @return MIDIメタメッセージのバイト列
	 */
	public byte[] getTempoByteArray() {
		return MIDISpec.qpmTempoToByteArray(getTempoInQpm());
	}
	/**
	 * テンポを設定します。
	 * @param qpm BPM(QPM)の値
	 */
	public void setTempo(int qpm) {
		tempoSpinnerModel.setValue(new Integer(qpm));
		tempoValueLabel.setText(""+qpm);
	}
	/**
	 * MIDIメタメッセージのバイト列からテンポを設定します。
	 * @param msgdata MIDIメタメッセージのバイト列（null を指定した場合はデフォルトに戻る）
	 */
	public void setTempo(byte msgdata[]) {
		setTempo(msgdata==null ? DEFAULT_QPM: MIDISpec.byteArrayToQpmTempo(msgdata));
	}
}

/**
 * 拍子選択ビュー
 */
class TimeSignatureSelecter extends JPanel implements MetaEventListener {
	SpinnerNumberModel upperTimesigSpinnerModel = new SpinnerNumberModel(4, 1, 32, 1);
	private JSpinner upperTimesigSpinner = new JSpinner(
		upperTimesigSpinnerModel
	) {
		{
			setToolTipText("Time signature (upper digit) - 拍子の分子");
		}
	};
	JComboBox<String> lowerTimesigCombobox = new JComboBox<String>() {
		{
			setToolTipText("Time signature (lower digit) - 拍子の分母");
			for( int i=0; i<6; i++ ) addItem( "/" + (1<<i) );
			setSelectedIndex(2);
		}
	};
	private class SetValueRunnable implements Runnable {
		byte[] qpm;
		public SetValueRunnable(byte[] qpm) { this.qpm = qpm; }
		@Override
		public void run() { setValue(qpm);}
	}
	@Override
	public void meta(MetaMessage msg) {
		switch(msg.getType()) {
		case 0x58: // Time signature (4 bytes) - 拍子
			if( ! SwingUtilities.isEventDispatchThread() ) {
				SwingUtilities.invokeLater(new SetValueRunnable(msg.getData()));
				break;
			}
			setValue(msg.getData());
			break;
		}
	}
	private class TimeSignatureLabel extends JLabel {
		private byte upper = -1;
		private byte lower_index = -1;
		{
			setToolTipText("Time signature - 拍子");
		}
		public void setTimeSignature(byte upper, byte lower_index) {
			if( this.upper == upper && this.lower_index == lower_index ) {
				return;
			}
			setText("<html><font size=\"+1\">" + upper + "/" + (1 << lower_index) + "</font></html>");
		}
	}
	private TimeSignatureLabel timesigValueLabel = new TimeSignatureLabel();
	private boolean	editable;
	public TimeSignatureSelecter() {
		add(upperTimesigSpinner);
		add(lowerTimesigCombobox);
		add(timesigValueLabel);
		setEditable(true);
	}
	public void clear() {
		upperTimesigSpinnerModel.setValue(4);
		lowerTimesigCombobox.setSelectedIndex(2);
	}
	public int getUpperValue() {
		return upperTimesigSpinnerModel.getNumber().intValue();
	}
	public byte getUpperByte() {
		return upperTimesigSpinnerModel.getNumber().byteValue();
	}
	public int getLowerValueIndex() {
		return lowerTimesigCombobox.getSelectedIndex();
	}
	public byte getLowerByte() {
		return (byte)getLowerValueIndex();
	}
	public byte[] getByteArray() {
		byte[] data = new byte[4];
		data[0] = getUpperByte();
		data[1] = getLowerByte();
		data[2] = (byte)( 96 >> getLowerValueIndex() );
		data[3] = 8;
		return data;
	}
	public void setValue(byte upper, byte lowerIndex) {
		upperTimesigSpinnerModel.setValue( upper );
		lowerTimesigCombobox.setSelectedIndex( lowerIndex );
		timesigValueLabel.setTimeSignature( upper, lowerIndex );
	}
	public void setValue(byte[] data) {
		if(data == null)
			clear();
		else
			setValue(data[0], data[1]);
	}
	public boolean isEditable() { return editable; }
	public void setEditable( boolean editable ) {
		this.editable = editable;
		upperTimesigSpinner.setVisible(editable);
		lowerTimesigCombobox.setVisible(editable);
		timesigValueLabel.setVisible(!editable);
		if( !editable ) {
			timesigValueLabel.setTimeSignature(getUpperByte(), getLowerByte());
		}
	}
}

/**
 * 調性選択
 */
class KeySignatureSelecter extends JPanel implements ActionListener {
	JComboBox<String> keysigCombobox = new JComboBox<String>() {
		{
			String str;
			Music.Key key;
			for( int i = -7 ; i <= 7 ; i++ ) {
				str = (key = new Music.Key(i)).toString();
				if( i != 0 ) {
					str = key.signature() + " : " + str ;
				}
				addItem(str);
			}
			setMaximumRowCount(15);
		}
	};
	JCheckBox minorCheckbox = null;

	public KeySignatureSelecter() { this(true); }
	public KeySignatureSelecter(boolean use_minor_checkbox) {
		add(new JLabel("Key:"));
		add(keysigCombobox);
		if( use_minor_checkbox ) {
			add( minorCheckbox = new JCheckBox("minor") );
			minorCheckbox.addActionListener(this);
		}
		keysigCombobox.addActionListener(this);
		clear();
	}
	public void actionPerformed(ActionEvent e) { updateToolTipText(); }
	private void updateToolTipText() {
		Music.Key key = getKey();
		keysigCombobox.setToolTipText(
			"Key: " + key.toStringIn( Music.SymbolLanguage.NAME )
			+ " "  + key.toStringIn( Music.SymbolLanguage.IN_JAPANESE )
			+ " (" + key.signatureDescription() + ")"
		);
	}
	public void clear() { setKey(new Music.Key("C")); }
	public void setKey( Music.Key key ) {
		if( key == null ) {
			clear();
			return;
		}
		keysigCombobox.setSelectedIndex( key.toCo5() + 7 );
		if( minorCheckbox == null )
			return;
		switch( key.majorMinor() ) {
		case Music.Key.MINOR : minorCheckbox.setSelected(true); break;
		case Music.Key.MAJOR : minorCheckbox.setSelected(false); break;
		}
	}
	public Music.Key getKey() {
		int minor = (
			minorCheckbox == null ? Music.Key.MAJOR_OR_MINOR :
			isMinor() ? Music.Key.MINOR :
			Music.Key.MAJOR
		);
		return new Music.Key(getKeyCo5(),minor);
	}
	public int getKeyCo5() {
		return keysigCombobox.getSelectedIndex() - 7;
	}
	public boolean isMinor() {
		return minorCheckbox != null && minorCheckbox.isSelected();
	}
}
/**
 * 調表示ラベル
 */
class KeySignatureLabel extends JLabel {
	private Music.Key key;
	public KeySignatureLabel() { clear(); }
	public Music.Key getKey() { return key; }
	public void setKeySignature( Music.Key key ) {
		this.key = key;
		if( key == null ) {
			setText("Key:C");
			setToolTipText("Key: Unknown");
			setEnabled(false);
			return;
		}
		setText( "key:" + key.toString() );
		setToolTipText(
			"Key: " + key.toStringIn(Music.SymbolLanguage.NAME)
			+ " "  + key.toStringIn(Music.SymbolLanguage.IN_JAPANESE)
			+ " (" + key.signatureDescription() + ")"
		);
		setEnabled(true);
	}
	public void clear() { setKeySignature( (Music.Key)null ); }
}

/**
 * ベロシティ選択ビュー
 */
class VelocitySelecter extends JPanel implements ChangeListener {
	private static final String	LABEL_PREFIX = "Velocity=";
	public JSlider slider;
	public JLabel label;
	public VelocitySelecter(BoundedRangeModel model) {
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(label = new JLabel(LABEL_PREFIX + model.getValue(), Label.RIGHT) {{
			setToolTipText("Velocity");
		}});
		add(slider = new JSlider(model) {{ setToolTipText("Velocity"); }});
		slider.addChangeListener(this);
	}
	public void stateChanged(ChangeEvent e) {
		label.setText( LABEL_PREFIX + getValue() );
	}
	@Override
	public void setBackground(Color c) {
		super.setBackground(c);
		// このクラスが構築される前にスーパークラスの
		// Look & Feel からここが呼ばれることがあるため
		// null チェックが必要
		if( slider != null ) slider.setBackground(c);
	}
	public int getValue() { return slider.getValue(); }
	public void setValue(int velocity) { slider.setValue(velocity); }
}

/**
 * MIDI Instrument (Program) - 音色選択
 */
class MidiProgramSelecter extends JComboBox<String> {
	private int family;
	private MidiProgramFamilySelecter family_selecter = null;
	public MidiProgramSelecter() {
		setFamily(-1);
	}
	public void setFamilySelecter( MidiProgramFamilySelecter mpfs ) {
		family_selecter = mpfs;
	}
	public void setFamily( int family ) {
		int program_no = getProgram();
		this.family = family;
		removeAllItems();
		if( family < 0 ) {
			setMaximumRowCount(16);
			for( int i=0; i < MIDISpec.instrument_names.length; i++ ) {
				addItem(i+": " + MIDISpec.instrument_names[i]);
			}
			setSelectedIndex(program_no);
		}
		else {
			setMaximumRowCount(8);
			for( int i=0; i < 8; i++ ) {
				program_no = i + family * 8;
				addItem( program_no + ": " + MIDISpec.instrument_names[program_no] );
			}
			setSelectedIndex(0);
		}
	}
	public int getProgram() {
		int program_no = getSelectedIndex();
		if( family > 0 && program_no >= 0 ) program_no += family * 8;
		return program_no;
	}
	public String getProgramName() { return (String)( getSelectedItem() ); }
	public void setProgram( int program_no ) {
		if( getItemCount() == 0 ) return; // To ignore event triggered by removeAllItems()
		if( family >= 0 && program_no >= 0 && family == program_no / 8 ) {
			setSelectedIndex(program_no % 8);
		}
		else {
			if( family >= 0 ) setFamily(-1);
			if( family_selecter != null ) family_selecter.setSelectedIndex(0);
			if( program_no < getItemCount() ) setSelectedIndex(program_no);
		}
	}
}
/**
 * MIDI Instrument (Program) Family - 音色ファミリーの選択
 */
class MidiProgramFamilySelecter extends JComboBox<String> implements ActionListener {
	private MidiProgramSelecter programSelecter = null;
	public MidiProgramFamilySelecter() { this(null); }
	public MidiProgramFamilySelecter( MidiProgramSelecter mps ) {
		programSelecter = mps;
		setMaximumRowCount(17);
		addItem("Program:");
		for( int i=0; i < MIDISpec.instrument_family_names.length; i++ ) {
			addItem( (i*8) + "-" + (i*8+7) + ": " + MIDISpec.instrument_family_names[i] );
		}
		setSelectedIndex(0);
		addActionListener(this);
	}
	public void actionPerformed(ActionEvent event) {
		if( programSelecter == null ) return;
		int i = getSelectedIndex();
		programSelecter.setFamily( i < 0 ? i : i-1 );
	}
	public int getProgram() {
		int i = getSelectedIndex();
		if( i <= 0 ) return -1;
		else return (i-1)*8;
	}
	public String getProgramFamilyName() { return (String)( getSelectedItem() ); }
	public void setProgram( int programNumber ) {
		if( programNumber < 0 ) programNumber = 0;
		else programNumber = programNumber / 8 + 1;
		setSelectedIndex( programNumber );
	}
}
