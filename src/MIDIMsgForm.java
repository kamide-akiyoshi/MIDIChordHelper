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
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultBoundedRangeModel;
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
	MidiMessageForm midiMessageForm = new MidiMessageForm();
	TickPositionForm tick_position_form = new TickPositionForm();
	JButton okButton = new JButton("OK");
	JButton cancel_button = new JButton("Cancel");
	public MidiEventDialog() {
		setLayout(new FlowLayout());
		add( tick_position_form );
		add( midiMessageForm );
		JPanel ok_cancel_panel = new JPanel();
		ok_cancel_panel.add( okButton );
		ok_cancel_panel.add( cancel_button );
		add( ok_cancel_panel );
		cancel_button.addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					setVisible(false);
				}
			}
		);
	}
	public void openTickForm() {
		tick_position_form.setVisible(true);
		midiMessageForm.setVisible(false);
		setBounds( 200, 300, 500, 120 );
		setVisible(true);
	}
	public void openEventForm() {
		tick_position_form.setVisible(true);
		midiMessageForm.setVisible(true);
		midiMessageForm.setDurationVisible(true);
		setBounds( 200, 300, 630, 320 );
		setVisible(true);
	}
	public void openMessageForm() {
		tick_position_form.setVisible(false);
		midiMessageForm.setVisible(true);
		midiMessageForm.setDurationVisible(false);
		setBounds( 200, 300, 630, 270 );
		setVisible(true);
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
	HexSelecter statusText = new HexSelecter("Status/Command");
	HexSelecter data1Text = new HexSelecter("[Data1] ");
	HexSelecter data2Text = new HexSelecter("[Data2] ");
	MidiChannelComboSelecter channelText =
		new MidiChannelComboSelecter("MIDI Channel");

	JComboBox<String> statusComboBox = statusText.getComboBox();
	JComboBox<String> data1ComboBox = data1Text.getComboBox();
	JComboBox<String> data2ComboBox = data2Text.getComboBox();
	JComboBox<Integer> channelComboBox = channelText.getComboBox();

	/**
	 * 長い値（テキストまたは数値）の入力欄
	 */
	HexTextForm dataText = new HexTextForm("Data:",3,50);
	/**
	 * 音階入力用ピアノキーボード
	 */
	PianoKeyboardPanel keyboardPanel = new PianoKeyboardPanel() {
		{
			keyboard.setPreferredSize(new Dimension(300,40));
			keyboard.addPianoKeyboardListener(
				new PianoKeyboardAdapter() {
					public void pianoKeyPressed(int n, InputEvent e) {
						data1Text.setValue(n);
						if( midi_channels != null )
							midi_channels[channelText.getSelectedChannel()].
							noteOn( n, data2Text.getValue() );
					}
					public void pianoKeyReleased(int n, InputEvent e) {
						if( midi_channels != null ) {
							midi_channels[channelText.getSelectedChannel()].
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
			minor_checkbox.addItemListener(
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
	private MidiChannel[] midi_channels = null;

	/**
	 * Note on/off のときに Duration フォームを表示するか
	 */
	private boolean is_duration_visible = true;

	public MidiMessageForm() {
		//
		// Set models
		//
		statusComboBox.setModel( statusComboBoxModel );
		statusComboBox.setSelectedIndex(1); // NoteOn
		data2ComboBox.setModel( hexData2ComboBoxModel );
		data2ComboBox.setSelectedIndex(64); // Center
		//
		// Add listener
		//
		statusComboBox.addActionListener(this);
		channelComboBox.addActionListener(this);
		data1ComboBox.addActionListener(this);
		//
		// Layout
		//
		JPanel panel1 = new JPanel();
		panel1.add( statusText );
		panel1.add( channelText );

		JPanel panel2 = new JPanel();
		panel2.add( data1Text );
		panel2.add( keyboardPanel );

		JPanel panel3 = new JPanel();
		panel3.add( data2Text );

		setLayout(new BoxLayout( this, BoxLayout.Y_AXIS ));
		add( panel1 );
		add( durationForm );
		add( panel2 );
		add( panel3 );
		add( tempoSelecter );
		add( timesigSelecter );
		add( keysigSelecter );
		add( dataText );

		updateVisible();
	}
	// ActionListener
	//
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
				case 0x51: dataText.setValue( tempoSelecter.getTempoByteArray() ); break;
				case 0x58: dataText.setValue( timesigSelecter.getByteArray() ); break;
				case 0x59: dataText.setValue( keysigSelecter.getKey().getBytes() ); break;
				default: break;
				}
			}
		}
		updateVisible();
	}
	//
	// Methods
	//
	public void setOutputMidiChannels( MidiChannel midi_channels[] ) {
		this.midi_channels = midi_channels;
	}
	public void setDurationVisible(boolean is_visible) {
		is_duration_visible = is_visible;
		updateVisible();
	}
	public boolean isDurationVisible() {
		return is_duration_visible;
	}
	public void updateVisible() {
		int msg_status = statusText.getValue();
		boolean is_ch_msg = MIDISpec.isChannelMessage(msg_status);
		channelText.setVisible(is_ch_msg);
		statusText.setTitle(
				"[Status] "+(is_ch_msg ? "Command" : "")
				);
		durationForm.setVisible( is_duration_visible && isNote(msg_status) );
		keyboardPanel.setVisible( msg_status <= 0xAF );

		if(
				msg_status <= 0xEF
				||
				msg_status >= 0xF1 && msg_status <= 0xF3
				||
				msg_status == 0xFF
				) {
			data1Text.setVisible( true );
		}
		else {
			data1Text.setVisible( false );
		}

		if(
				(msg_status >= 0xC0 && msg_status <= 0xDF) ||
				msg_status == 0xF0 || msg_status == 0xF1 ||
				msg_status == 0xF3 || msg_status >= 0xF6
				) {
			data2Text.setVisible( false );
		}
		else {
			data2Text.setVisible( true );
		}
		data2Text.setTitle("[Data2] "+(
				msg_status <= 0x9F ? "Velocity" :
					msg_status <= 0xAF ? "Pressure" :
						msg_status <= 0xBF ? "Value" :
							(msg_status & 0xF0) == 0xE0 ? "High 7bit value" : ""
				));

		// Show if Sysex or Meta
		dataText.setVisible(
				msg_status == 0xF0 ||
				msg_status == 0xF7 ||
				msg_status == 0xFF
				);

		if( msg_status != 0xFF ) {
			tempoSelecter.setVisible(false);
			timesigSelecter.setVisible(false);
			keysigSelecter.setVisible(false);
		}

		switch( msg_status & 0xF0 ) {
		// ステータスに応じて、１バイト目のデータモデルを切り替える。

		case 0x80: // Note Off
		case 0x90: // Note On
		case 0xA0: // Polyphonic Key Pressure
			int ch = channelText.getSelectedChannel();
			data1Text.setTitle(
					"[Data1] "+( ch == 9 ? "Percussion" : "Note No." )
					);
			data1ComboBox.setModel(
					ch == 9 ? percussionComboBoxModel : noteComboBoxModel
					);
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
			if( msg_status == 0xFF ) { // MetaMessage
				data1Text.setTitle("[Data1] MetaEvent Type");
				data1ComboBox.setModel(metaTypeComboBoxModel);
				int msg_type = data1Text.getValue();
				tempoSelecter.setVisible( msg_type == 0x51 );
				timesigSelecter.setVisible( msg_type == 0x58 );
				keysigSelecter.setVisible( msg_type == 0x59 );
				//
				if( MIDISpec.isEOT(msg_type) ) {
					dataText.clear();
					dataText.setVisible(false);
				}
				else {
					dataText.setTitle(
							MIDISpec.hasMetaText( msg_type ) ? "Text:":"Data:"
							);
				}
			}
			else {
				data1Text.setTitle("[Data1] ");
				data1ComboBox.setModel(hexData1ComboBoxModel);
			}
			break;
		}
	}
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
	public boolean setNote( int ch, int note_no, int velocity ) {
		channelText.setSelectedChannel(ch);
		data1Text.setValue(note_no);
		data2Text.setValue(velocity);
		return true;
	}
	public boolean isNote() {
		return isNote( statusText.getValue() );
	}
	public boolean isNote( int status ) {
		int cmd = status & 0xF0;
		return ( cmd == ShortMessage.NOTE_ON || cmd == ShortMessage.NOTE_OFF );
	}
	public boolean isNote( boolean note_on ) {
		return isNote( note_on, statusText.getValue() );
	}
	public boolean isNote( boolean note_on, int status ) {
		int cmd = status & 0xF0;
		return (
				note_on && cmd == ShortMessage.NOTE_ON && data2Text.getValue() > 0
				||
				!note_on && (
						cmd == ShortMessage.NOTE_ON && data2Text.getValue() <= 0
						||
						cmd == ShortMessage.NOTE_OFF
						)
				);
	}
	public ShortMessage getPartnerMessage() {
		ShortMessage sm = (ShortMessage)getMessage();
		if( sm == null ) return null;
		ShortMessage partner_sm;
		if( isNote(true) ) { // NoteOn
			partner_sm = new ShortMessage();
		try{
			partner_sm.setMessage(
					ShortMessage.NOTE_OFF,
					sm.getChannel(),
					sm.getData1(), sm.getData2()
					);
		} catch( InvalidMidiDataException e ) {
			e.printStackTrace();
			return null;
		}
		return partner_sm;
		}
		else if( isNote(false) ) { // NoteOff
			partner_sm = new ShortMessage();
			try{
				partner_sm.setMessage(
						ShortMessage.NOTE_ON,
						sm.getChannel(),
						sm.getData1() == 0 ? 100 : sm.getData1(),
								sm.getData2()
						);
			} catch( InvalidMidiDataException e ) {
				e.printStackTrace();
				return null;
			}
			return partner_sm;
		}
		return null;
	}
}


///////////////////////////////////////
//
// Hex value: [0x00 0x00 0x00 ... ]
//
///////////////////////////////////////
class HexTextForm extends JPanel {
	public JTextArea text_area;
	public JLabel title_label;
	public HexTextForm(String title) {
		this(title,1,3);
	}
	public HexTextForm(String title, int rows, int columns) {
		if( title != null ) add(title_label = new JLabel(title));
		text_area = new JTextArea(rows, columns);
		text_area.setLineWrap(true);
		JScrollPane text_scroll_pane = new JScrollPane(text_area);
		add(text_scroll_pane);
		setLayout(new FlowLayout());
	}
	public String getString() {
		return text_area.getText();
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
		title_label.setText( str );
	}
	public void setString( String str ) {
		text_area.setText( str );
	}
	public void setValue( int val ) {
		text_area.setText( String.format( " 0x%02X", val ) );
	}
	public void setValue( byte val ) {
		text_area.setText( String.format( " 0x%02X", val ) );
	}
	public void setValue( byte ba[] ) {
		String str = "";
		for( byte b : ba ) {
			str += String.format( " 0x%02X", b );
		}
		text_area.setText(str);
	}
	public void clear() { text_area.setText(""); }
}

///////////////////////////////////////
//
// Hex value: [0x00 0x00 0x00 ... ] v -> Select
//
///////////////////////////////////////
class HexSelecter extends JPanel {
	private JComboBox<String> comboBox = new JComboBox<String>();
	private JLabel title;
	public HexSelecter( String title ) {
		if( title != null )
			add( this.title = new JLabel(title) );
		add(comboBox);
		setLayout(new FlowLayout());
		comboBox.setEditable(true);
		comboBox.setMaximumRowCount(16);
	}
	public JComboBox<String> getComboBox() {
		return comboBox;
	}
	public void setTitle( String title ) {
		this.title.setText(title);
	}
	public String getString() {
		return (String)( comboBox.getSelectedItem() );
	}
	public int getValue() {
		ArrayList<Integer> ia = getIntegerList();
		return ia.size() == 0 ? -1 : ia.get(0);
	}
	public ArrayList<Integer> getIntegerList() {
		String words[], str = getString();
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

///////////////////////////////////////
//
// MIDI Channel Selecter (ComboBox or List)
//
///////////////////////////////////////
interface MidiChannelComboBoxModel extends ComboBoxModel<Integer> {
	int getSelectedChannel();
	void setSelectedChannel(int channel);
}
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
class MidiChannelComboSelecter extends JPanel {
	JComboBox<Integer> comboBox = new JComboBox<>();
	public MidiChannelComboSelecter( String title ) {
		this( title, new DefaultMidiChannelComboBoxModel() );
	}
	public MidiChannelComboSelecter(
		String title, MidiChannelComboBoxModel model
	) {
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
class MidiChannelButtonSelecter extends JList<Integer> {
	private PianoKeyboard keyboard = null;
	public MidiChannelButtonSelecter(MidiChannelComboBoxModel model) {
		super(model);
		setLayoutOrientation(HORIZONTAL_WRAP);
		setVisibleRowCount(1);
		setCellRenderer(new MyCellRenderer());
		setSelectedIndex(model.getSelectedChannel());
		model.addListDataListener(new ListDataListener() {
			public void contentsChanged(ListDataEvent e) {
				MidiChannelButtonSelecter.this.setSelectedIndex(
						MidiChannelButtonSelecter.this.getModel().getSelectedChannel()
						);
			}
			public void intervalAdded(ListDataEvent e) {}
			public void intervalRemoved(ListDataEvent e) {}
		});
		addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				MidiChannelButtonSelecter.this.getModel().setSelectedChannel(
						MidiChannelButtonSelecter.this.getSelectedIndex()
						);
			}
		});
	}
	public MidiChannelButtonSelecter(PianoKeyboard keyboard) {
		this(keyboard.midiChComboboxModel);
		setPianoKeyboard(keyboard);
	}
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
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			if( cellHasFocus ) {
				g.setColor(Color.gray);
				g.drawRect(0, 0, this.getWidth() - 1, this.getHeight() - 1);
			}
		}
	}
}

////////////////////////////////////
//
// Tick Position
//
// Mesausre:[xxxx] Beat:[xx] ExTick:[xxx]
//
////////////////////////////////////
class TickPositionModel implements ChangeListener {
	private SequenceTickIndex seq_index;
	private boolean is_changing = false;
	public SpinnerNumberModel
	tick_model, measure_model, beat_model, extra_tick_model;
	//
	// Constuctor
	//
	public TickPositionModel() {
		tick_model = new SpinnerNumberModel(0L, 0L, 999999L, 1L);
		tick_model.addChangeListener(this);
		measure_model = new SpinnerNumberModel(1, 1, 9999, 1);
		measure_model.addChangeListener(this);
		beat_model = new SpinnerNumberModel(1, 1, 32, 1);
		beat_model.addChangeListener(this);
		extra_tick_model = new SpinnerNumberModel(0, 0, 4*960-1, 1);
		extra_tick_model.addChangeListener(this);
	}
	// ChangeListener
	//
	public void stateChanged(ChangeEvent e) {
		if( seq_index == null ) return;
		Object src = e.getSource();
		if( src == tick_model ) {
			is_changing = true;
			measure_model.setValue(
					1 + seq_index.tickToMeasure(
							tick_model.getNumber().longValue()
							)
					);
			beat_model.setValue( seq_index.lastBeat + 1 );
			is_changing = false;
			extra_tick_model.setValue( seq_index.lastExtraTick );
			return;
		}
		if( is_changing ) return;
		tick_model.setValue(
				seq_index.measureToTick(
						measure_model.getNumber().intValue() - 1,
						beat_model.getNumber().intValue() - 1,
						extra_tick_model.getNumber().intValue()
						)
				);
	}
	// Methods
	//
	public SequenceTickIndex getSequenceIndex() {
		return seq_index;
	}
	public void setSequenceIndex( SequenceTickIndex seq_index ) {
		this.seq_index = seq_index;
		extra_tick_model.setMaximum( 4 * seq_index.getResolution() - 1 );
	}
	public long getTickPosition() {
		return tick_model.getNumber().longValue();
	}
	public void setTickPosition( long tick ) {
		tick_model.setValue(tick);
	}
}

class TickPositionForm extends JPanel {
	JSpinner
	tick_spinner = new JSpinner(),
	measure_spinner = new JSpinner(),
	beat_spinner = new JSpinner(),
	extra_tick_spinner = new JSpinner();

	public TickPositionForm() {
		setLayout(new GridLayout(2,4));
		add( new JLabel() );
		add( new JLabel() );
		add( new JLabel("Measure:") );
		add( new JLabel("Beat:") );
		add( new JLabel("ExTick:") );
		add( new JLabel("Tick position : ",JLabel.RIGHT) );
		add( tick_spinner );
		add( measure_spinner );
		add( beat_spinner );
		add( extra_tick_spinner );
	}
	public TickPositionForm( TickPositionModel tpm ) {
		this(); setModel(tpm);
	}
	public void setModel( TickPositionModel tpm ) {
		tick_spinner.setModel(tpm.tick_model);
		measure_spinner.setModel(tpm.measure_model);
		beat_spinner.setModel(tpm.beat_model);
		extra_tick_spinner.setModel(tpm.extra_tick_model);
	}
}

/**
 * 音の長さフォーム
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
	// ActionListener
	//
	public void actionPerformed(ActionEvent e) {
		int duration = note_combo.getDuration();
		if( duration < 0 ) return;
		model.setDuration( duration );
	}
	// ChangeListener
	//
	public void stateChanged(ChangeEvent e) {
		note_combo.setDuration( model.getDuration() );
	}
	// Methods
	//
	public void setEnabled( boolean enabled ) {
		super.setEnabled(enabled);
		title_label.setEnabled(enabled);
		spinner.setEnabled(enabled);
		note_combo.setEnabled(enabled);
		unit_label.setEnabled(enabled);
	}
	public void setPPQ( int ppq ) {
		model.setPPQ( this.ppq = ppq );
	}
	public int getDuration() {
		return model.getDuration();
	}
	public void setDuration( int duration ) {
		model.setDuration(duration);
	}
}

//////////////////////////////
//
//  Tempo in QPM
//
//////////////////////////////
class TempoSelecter extends JPanel implements MouseListener {
	JSpinner		tempo_spinner;
	SpinnerNumberModel	tempoSpinnerModel;
	JLabel		tempo_label, tempo_value_label;
	private boolean	editable;
	static final int	DEFAULT_QPM = 120;
	private long	prev_beat_us_pos = 0;

	public TempoSelecter() {
		String tool_tip = "Tempo in quatrers per minute - テンポ（１分あたりの四分音符の数）";
		tempo_label = new JLabel(
			"=",
			new ButtonIcon( ButtonIcon.QUARTER_NOTE_ICON ),
			JLabel.CENTER
		);
		tempo_label.setVerticalAlignment( JLabel.CENTER );
		tempoSpinnerModel = new SpinnerNumberModel(DEFAULT_QPM, 1, 999, 1);
		tempo_spinner = new JSpinner( tempoSpinnerModel );
		tempo_spinner.setToolTipText( tool_tip );
		tempo_value_label = new JLabel( ""+DEFAULT_QPM );
		tempo_value_label.setToolTipText( tool_tip );
		setLayout( new BoxLayout(this,BoxLayout.X_AXIS) );
		add( tempo_label );
		add( Box.createHorizontalStrut(5) );
		add( tempo_spinner );
		add( tempo_value_label );
		setEditable(true);
		tempo_label.addMouseListener(this);
	}
	//
	// MouseListener
	//
	public void mousePressed(MouseEvent e) {
		Component obj = e.getComponent();
		if( obj == tempo_label && isEditable() ) {
			//
			// Adjust tempo by interval time between two clicks
			//
			long current_us = System.nanoTime()/1000;
			// midi_ch_selecter.noteOn( 9, 37, 100 );
			long interval_us = current_us - prev_beat_us_pos;
			prev_beat_us_pos = current_us;
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
	//
	// Methods
	//
	public boolean isEditable() {
		return editable;
	}
	public void setEditable( boolean editable ) {
		this.editable = editable;
		tempo_spinner.setVisible( editable );
		tempo_value_label.setVisible( !editable );
		if( !editable ) {
			// Copy spinner's value to label
			tempo_value_label.setText(
				""+tempoSpinnerModel.getNumber().intValue()
			);
		}
		tempo_label.setToolTipText(
			editable ?
			"Click rhythmically to adjust tempo - ここをクリックしてリズムをとるとテンポを合わせられます"
			: null
		);
	}
	public int getTempoInQpm() {
		return tempoSpinnerModel.getNumber().intValue();
	}
	public byte[] getTempoByteArray() {
		return MIDISpec.qpmTempoToByteArray( getTempoInQpm() );
	}
	public void setTempo( int qpm ) {
		tempoSpinnerModel.setValue(new Integer(qpm));
		tempo_value_label.setText(""+qpm);
	}
	public void setTempo( byte msgdata[] ) {
		setTempo( MIDISpec.byteArrayToQpmTempo( msgdata ) );
	}
	public void clear() { setTempo( DEFAULT_QPM ); }
}

/**
 * 拍子選択ビュー
 */
class TimeSignatureSelecter extends JPanel {
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
	public void setValue( byte upper, byte lower_index ) {
		upperTimesigSpinnerModel.setValue( upper );
		lowerTimesigCombobox.setSelectedIndex( lower_index );
		timesigValueLabel.setTimeSignature( upper, lower_index );
	}
	public void setValue( byte[] data ) {
		setValue( data[0], data[1] );
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

/////////////////////////////
//
// Key Signature - 調性選択
//
/////////////////////////////
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
	JCheckBox minor_checkbox = null;

	public KeySignatureSelecter() {
		this(true);
	}
	public KeySignatureSelecter(boolean use_minor_checkbox) {
		add(new JLabel("Key:"));
		add(keysigCombobox);
		if( use_minor_checkbox ) {
			add( minor_checkbox = new JCheckBox("minor") );
			minor_checkbox.addActionListener(this);
		}
		keysigCombobox.addActionListener(this);
		clear();
	}
	// ActionListener
	//
	public void actionPerformed(ActionEvent e) {
		updateToolTipText();
	}
	//
	// Methods
	//
	private void updateToolTipText() {
		Music.Key key = getKey();
		keysigCombobox.setToolTipText(
			"Key: " + key.toStringIn( Music.SymbolLanguage.NAME )
			+ " "  + key.toStringIn( Music.SymbolLanguage.IN_JAPANESE )
			+ " (" + key.signatureDescription() + ")"
		);
	}
	public void clear() {
		setKey( new Music.Key("C") );
	}
	public void setKey( Music.Key key ) {
		if( key == null ) {
			clear();
			return;
		}
		keysigCombobox.setSelectedIndex( key.toCo5() + 7 );
		if( minor_checkbox == null )
			return;
		switch( key.majorMinor() ) {
		case Music.Key.MINOR : minor_checkbox.setSelected(true); break;
		case Music.Key.MAJOR : minor_checkbox.setSelected(false); break;
		}
	}
	public Music.Key getKey() {
		int minor = (
			minor_checkbox == null ? Music.Key.MAJOR_OR_MINOR :
			isMinor() ? Music.Key.MINOR : Music.Key.MAJOR
		);
		return new Music.Key(getKeyCo5(),minor);
	}
	public int getKeyCo5() {
		return keysigCombobox.getSelectedIndex() - 7;
	}
	public boolean isMinor() {
		return ( minor_checkbox != null && minor_checkbox.isSelected() );
	}
}
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

///////////////////////////////////////////////
//
// Velocity
//
///////////////////////////////////////////////
class VelocityModel extends DefaultBoundedRangeModel {
	public VelocityModel() { super( 64, 0, 0, 127 ); }
}
class VelocitySelecter extends JPanel {
	private static final String	LABEL_PREFIX = "Velocity=";
	public JSlider slider = null;
	public JLabel label;
	public VelocitySelecter( VelocityModel model ) {
		slider = new JSlider(model);
		slider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				label.setText( LABEL_PREFIX + getValue() );
			}
		});
		slider.setToolTipText("Velocity");
		label = new JLabel( LABEL_PREFIX + model.getValue(), Label.RIGHT );
		label.setToolTipText("Velocity");
		setLayout( new BoxLayout( this, BoxLayout.X_AXIS ) );
		add(label);
		add(slider);
	}
	public void setBackground(Color c) {
		super.setBackground(c);
		if( slider != null ) slider.setBackground(c);
	}
	public int getValue() {
		return slider.getValue();
	}
	public void setValue(int velocity) {
		slider.setValue(velocity);
	}
}

///////////////////////////////////////////////
//
// MIDI Instrument (Program) - 音色選択
//
///////////////////////////////////////////////
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
class MidiProgramFamilySelecter extends JComboBox<String> implements ActionListener {
	private MidiProgramSelecter program_selecter = null;
	public MidiProgramFamilySelecter() { this(null); }
	public MidiProgramFamilySelecter( MidiProgramSelecter mps ) {
		program_selecter = mps;
		setMaximumRowCount(17);
		addItem("Program:");
		for( int i=0; i < MIDISpec.instrument_family_names.length; i++ ) {
			addItem( (i*8) + "-" + (i*8+7) + ": " + MIDISpec.instrument_family_names[i] );
		}
		setSelectedIndex(0);
		addActionListener(this);
	}
	public void actionPerformed(ActionEvent evt) {
		if( program_selecter == null ) return;
		int i = getSelectedIndex();
		program_selecter.setFamily( i < 0 ? i : i-1 );
	}
	public int getProgram() {
		int i = getSelectedIndex();
		if( i <= 0 ) return -1;
		else return (i-1)*8;
	}
	public String getProgramFamilyName() { return (String)( getSelectedItem() ); }
	public void setProgram( int program_no ) {
		if( program_no < 0 ) program_no = 0;
		else program_no = program_no / 8 + 1;
		setSelectedIndex( program_no );
	}
}
