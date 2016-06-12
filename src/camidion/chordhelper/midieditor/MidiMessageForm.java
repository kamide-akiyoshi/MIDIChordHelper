package camidion.chordhelper.midieditor;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.nio.charset.Charset;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.swing.BoxLayout;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import camidion.chordhelper.music.Key;
import camidion.chordhelper.music.MIDISpec;
import camidion.chordhelper.music.NoteSymbol;
import camidion.chordhelper.pianokeyboard.PianoKeyboardAdapter;
import camidion.chordhelper.pianokeyboard.PianoKeyboardPanel;

/**
 * MIDI Message Entry Form - MIDIメッセージ入力欄
 */
public class MidiMessageForm extends JPanel implements ActionListener {
	/**
	 * MIDIステータス
	 */
	private ComboBoxModel<String> statusComboBoxModel =
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
	private ComboBoxModel<String> noteComboBoxModel =
		new DefaultComboBoxModel<String>() {
			{
				for( int i = 0; i<=0x7F; i++ ) addElement(
					String.format(
						"0x%02X : %d : %s", i, i, NoteSymbol.noteNoToSymbol(i)
					)
				);
				// Center note C
				setSelectedItem(getElementAt(60));
			}
		};
	/**
	 * 打楽器名
	 */
	private ComboBoxModel<String> percussionComboBoxModel =
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
	private ComboBoxModel<String> controlChangeComboBoxModel =
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
	private ComboBoxModel<String> instrumentComboBoxModel =
		new DefaultComboBoxModel<String>() {
			{
				for( int i = 0; i<=0x7F; i++ ) addElement(
					String.format(
						"0x%02X : %s", i, MIDISpec.instrumentNames[i]
					)
				);
			}
		};
	/**
	 * MetaMessage Type
	 */
	private ComboBoxModel<String> metaTypeComboBoxModel =
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
	private ComboBoxModel<String> hexData1ComboBoxModel =
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
	private ComboBoxModel<String> hexData2ComboBoxModel =
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
	public DurationForm durationForm = new DurationForm();
	/**
	 * テンポ選択
	 */
	private TempoSelecter tempoSelecter = new TempoSelecter() {
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
	private TimeSignatureSelecter timesigSelecter = new TimeSignatureSelecter() {
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
	private KeySignatureSelecter keysigSelecter = new KeySignatureSelecter() {
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

		case ShortMessage.NOTE_OFF:
		case ShortMessage.NOTE_ON:
		case ShortMessage.POLY_PRESSURE:
			int ch = channelText.getSelectedChannel();
			data1Text.setTitle("[Data1] "+(ch == 9 ? "Percussion" : "Note No."));
			data1ComboBox.setModel(ch == 9 ? percussionComboBoxModel : noteComboBoxModel);
			break;

		case ShortMessage.CONTROL_CHANGE: // Control Change / Mode Change
			data1Text.setTitle("[Data1] Control/Mode No.");
			data1ComboBox.setModel(controlChangeComboBoxModel);
			break;

		case ShortMessage.PROGRAM_CHANGE:
			data1Text.setTitle( "[Data1] Program No.");
			data1ComboBox.setModel(instrumentComboBoxModel);
			break;

		case ShortMessage.CHANNEL_PRESSURE:
			data1Text.setTitle("[Data1] Pressure");
			data1ComboBox.setModel(hexData1ComboBoxModel);
			break;

		case ShortMessage.PITCH_BEND:
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
					dataText.setTitle(MIDISpec.hasMetaMessageText(msgType)?"Text:":"Data:");
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
	 * @param charset 文字コード
	 * @return 入力している内容から生成したMIDIメッセージ
	 */
	public MidiMessage getMessage(Charset charset) {
		int msgStatus = statusText.getValue();
		if( msgStatus < 0 ) {
			return null;
		}
		else if( msgStatus == 0xFF ) {
			int msgType = data1Text.getValue();
			if( msgType < 0 ) return null;
			byte msgData[];
			if( MIDISpec.hasMetaMessageText(msgType) ) {
				msgData = dataText.getString().getBytes(charset);
			}
			else if( msgType == 0x2F ) { // EOT
				// To avoid inserting un-removable EOT, ignore the data.
				msgData = new byte[0];
			}
			else {
				if( (msgData = dataText.getBytes() ) == null ) {
					return null;
				}
			}
			MetaMessage msg = new MetaMessage();
			try {
				msg.setMessage( msgType, msgData, msgData.length );
			} catch( InvalidMidiDataException e ) {
				e.printStackTrace();
				return null;
			}
			return (MidiMessage)msg;
		}
		else if( msgStatus == 0xF0 || msgStatus == 0xF7 ) {
			SysexMessage msg = new SysexMessage();
			byte data[] = dataText.getBytes();
			if( data == null ) return null;
			try {
				msg.setMessage(
						(int)(msgStatus & 0xFF), data, data.length
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
			if( MIDISpec.isChannelMessage( msgStatus ) ) {
				msg.setMessage(
					(msgStatus & 0xF0),
					channelText.getSelectedChannel(),
					msg_data1, msg_data2
				);
			}
			else {
				msg.setMessage( msgStatus, msg_data1, msg_data2 );
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
	public void setMessage(MidiMessage msg, Charset charset) {
		if( msg instanceof ShortMessage ) {
			ShortMessage smsg = (ShortMessage)msg;
			int msgChannel = 0;
			int msgStatus = smsg.getStatus();
			if( MIDISpec.isChannelMessage(msgStatus) ) {
				msgStatus = smsg.getCommand();
				msgChannel = smsg.getChannel();
			}
			statusText.setValue( msgStatus );
			channelText.setSelectedChannel( msgChannel );
			data1Text.setValue( smsg.getData1() );
			data2Text.setValue( smsg.getData2() );
		}
		else if( msg instanceof SysexMessage ) {
			SysexMessage sysexMsg = (SysexMessage)msg;
			statusText.setValue( sysexMsg.getStatus() );
			dataText.setValue( sysexMsg.getData() );
		}
		else if( msg instanceof MetaMessage ) {
			MetaMessage metaMsg = (MetaMessage)msg;
			int msgType = metaMsg.getType();
			byte data[] = metaMsg.getData();
			statusText.setValue( 0xFF );
			data1Text.setValue(msgType);
			switch(msgType) {
			case 0x51: tempoSelecter.setTempo(data); break;
			case 0x58: timesigSelecter.setValue(data[0], data[1]); break;
			case 0x59: keysigSelecter.setKey(new Key(data)); break;
			default: break;
			}
			if( MIDISpec.hasMetaMessageText(msgType) ) {
				dataText.setString(new String(data,charset));
			}
			else {
				dataText.setValue(data);
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
		ShortMessage sm = (ShortMessage)getMessage(Charset.defaultCharset());
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