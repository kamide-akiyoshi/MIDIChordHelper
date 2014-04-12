package camidion.chordhelper.midieditor;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Track;
import javax.swing.DefaultListSelectionModel;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

import camidion.chordhelper.music.Key;
import camidion.chordhelper.music.MIDISpec;
import camidion.chordhelper.music.NoteSymbol;
import camidion.chordhelper.music.SymbolLanguage;

/**
 * MIDIトラック（MIDIイベントリスト）テーブルモデル
 */
public class TrackEventListTableModel extends AbstractTableModel {
	/**
	 * 列
	 */
	public enum Column {
		/** MIDIイベント番号 */
		EVENT_NUMBER("No.", Integer.class, 15) {
			@Override
			public boolean isCellEditable() { return false; }
		},
		/** tick位置 */
		TICK_POSITION("TickPos.", Long.class, 40) {
			@Override
			public Object getValue(MidiEvent event) {
				return event.getTick();
			}
		},
		/** tick位置に対応する小節 */
		MEASURE_POSITION("Measure", Integer.class, 30) {
			public Object getValue(SequenceTrackListTableModel seq, MidiEvent event) {
				return seq.getSequenceTickIndex().tickToMeasure(event.getTick()) + 1;
			}
		},
		/** tick位置に対応する拍 */
		BEAT_POSITION("Beat", Integer.class, 20) {
			@Override
			public Object getValue(SequenceTrackListTableModel seq, MidiEvent event) {
				TrackEventListTableModel.SequenceTickIndex sti = seq.getSequenceTickIndex();
				sti.tickToMeasure(event.getTick());
				return sti.lastBeat + 1;
			}
		},
		/** tick位置に対応する余剰tick（拍に収まらずに余ったtick数） */
		EXTRA_TICK_POSITION("ExTick", Integer.class, 20) {
			@Override
			public Object getValue(SequenceTrackListTableModel seq, MidiEvent event) {
				TrackEventListTableModel.SequenceTickIndex sti = seq.getSequenceTickIndex();
				sti.tickToMeasure(event.getTick());
				return sti.lastExtraTick;
			}
		},
		/** MIDIメッセージ */
		MESSAGE("MIDI Message", String.class, 300) {
			@Override
			public Object getValue(SequenceTrackListTableModel seq, MidiEvent event) {
				return msgToString(event.getMessage(), seq.charset);
			}
		};
		private String title;
		private Class<?> columnClass;
		int preferredWidth;
		/**
		 * 列の識別子を構築します。
		 * @param title 列のタイトル
		 * @param widthRatio 幅の割合
		 * @param columnClass 列のクラス
		 * @param perferredWidth 列の適切な幅
		 */
		private Column(String title, Class<?> columnClass, int preferredWidth) {
			this.title = title;
			this.columnClass = columnClass;
			this.preferredWidth = preferredWidth;
		}
		/**
		 * セルを編集できるときtrue、編集できないときfalseを返します。
		 */
		public boolean isCellEditable() { return true; }
		/**
		 * 列の値を返します。
		 * @param event 対象イベント
		 * @return この列の対象イベントにおける値
		 */
		public Object getValue(MidiEvent event) { return ""; }
		/**
		 * 列の値を返します。
		 * @param sti 対象シーケンスモデル
		 * @param event 対象イベント
		 * @return この列の対象イベントにおける値
		 */
		public Object getValue(SequenceTrackListTableModel seq, MidiEvent event) {
			return getValue(event);
		}
	}
	/**
	 * ラップされているMIDIトラック
	 */
	private Track track;
	/**
	 * 親のシーケンスモデル
	 */
	SequenceTrackListTableModel sequenceTrackListTableModel;
	/**
	 * 選択されているイベントのインデックス
	 */
	ListSelectionModel eventSelectionModel = new DefaultListSelectionModel() {
		{
			setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		}
	};
	/**
	 * シーケンスを親にして、その特定のトラックに連動する
	 * MIDIトラックモデルを構築します。
	 *
	 * @param parent 親のシーケンスモデル
	 * @param track ラップするMIDIトラック（ない場合はnull）
	 */
	public TrackEventListTableModel(
		SequenceTrackListTableModel sequenceTrackListTableModel, Track track
	) {
		this.track = track;
		this.sequenceTrackListTableModel = sequenceTrackListTableModel;
	}
	@Override
	public int getRowCount() {
		return track == null ? 0 : track.size();
	}
	@Override
	public int getColumnCount() {
		return Column.values().length;
	}
	/**
	 * 列名を返します。
	 */
	@Override
	public String getColumnName(int column) {
		return Column.values()[column].title;
	}
	/**
	 * 列のクラスを返します。
	 */
	@Override
	public Class<?> getColumnClass(int column) {
		return Column.values()[column].columnClass;
	}
	@Override
	public Object getValueAt(int row, int column) {
		TrackEventListTableModel.Column c = Column.values()[column];
		if( c == Column.EVENT_NUMBER ) return row;
		MidiEvent event = track.get(row);
		switch(c) {
		case MEASURE_POSITION:
		case BEAT_POSITION:
		case EXTRA_TICK_POSITION:
		case MESSAGE:
			return c.getValue(sequenceTrackListTableModel, event);
		default:
			return c.getValue(event);
		}
	}
	/**
	 * セルを編集できるときtrue、編集できないときfalseを返します。
	 */
	@Override
	public boolean isCellEditable(int row, int column) {
		return Column.values()[column].isCellEditable();
	}
	/**
	 * セルの値を変更します。
	 */
	@Override
	public void setValueAt(Object value, int row, int column) {
		long newTick;
		switch(Column.values()[column]) {
		case TICK_POSITION: newTick = (Long)value; break;
		case MEASURE_POSITION:
			newTick = sequenceTrackListTableModel.getSequenceTickIndex().measureToTick(
				(Integer)value - 1,
				(Integer)getValueAt( row, Column.BEAT_POSITION.ordinal() ) - 1,
				(Integer)getValueAt( row, Column.EXTRA_TICK_POSITION.ordinal() )
			);
			break;
		case BEAT_POSITION:
			newTick = sequenceTrackListTableModel.getSequenceTickIndex().measureToTick(
				(Integer)getValueAt( row, Column.MEASURE_POSITION.ordinal() ) - 1,
				(Integer)value - 1,
				(Integer)getValueAt( row, Column.EXTRA_TICK_POSITION.ordinal() )
			);
			break;
		case EXTRA_TICK_POSITION:
			newTick = sequenceTrackListTableModel.getSequenceTickIndex().measureToTick(
				(Integer)getValueAt( row, Column.MEASURE_POSITION.ordinal() ) - 1,
				(Integer)getValueAt( row, Column.BEAT_POSITION.ordinal() ) - 1,
				(Integer)value
			);
			break;
		default: return;
		}
		MidiEvent oldMidiEvent = track.get(row);
		if( oldMidiEvent.getTick() == newTick ) {
			return;
		}
		MidiMessage msg = oldMidiEvent.getMessage();
		MidiEvent newMidiEvent = new MidiEvent(msg,newTick);
		track.remove(oldMidiEvent);
		track.add(newMidiEvent);
		fireTableDataChanged();
		if( MIDISpec.isEOT(msg) ) {
			// EOTの場所が変わると曲の長さが変わるので、親モデルへ通知する。
			sequenceTrackListTableModel.sequenceListTableModel.fireSequenceModified(sequenceTrackListTableModel);
		}
	}
	/**
	 * MIDIトラックを返します。
	 * @return MIDIトラック
	 */
	public Track getTrack() { return track; }
	/**
	 * トラック名を返します。
	 */
	@Override
	public String toString() {
		byte b[] = MIDISpec.getNameBytesOf(track);
		if( b == null ) return "";
		Charset cs = Charset.defaultCharset();
		if( sequenceTrackListTableModel != null )
			cs = sequenceTrackListTableModel.charset;
		return new String(b, cs);
	}
	/**
	 * トラック名を設定します。
	 * @param name トラック名
	 * @return 設定が行われたらtrue
	 */
	public boolean setString(String name) {
		if(name.equals(toString()))
			return false;
		byte b[] = name.getBytes(sequenceTrackListTableModel.charset);
		if( ! MIDISpec.setNameBytesOf(track, b) )
			return false;
		sequenceTrackListTableModel.setModified(true);
		sequenceTrackListTableModel.sequenceListTableModel.fireSequenceModified(sequenceTrackListTableModel);
		fireTableDataChanged();
		return true;
	}
	private String recordingChannel = "OFF";
	/**
	 * 録音中のMIDIチャンネルを返します。
	 * @return 録音中のMIDIチャンネル
	 */
	public String getRecordingChannel() { return recordingChannel; }
	/**
	 * 録音中のMIDIチャンネルを設定します。
	 * @param recordingChannel 録音中のMIDIチャンネル
	 */
	public void setRecordingChannel(String recordingChannel) {
		Sequencer sequencer = sequenceTrackListTableModel.sequenceListTableModel.sequencerModel.getSequencer();
		if( recordingChannel.equals("OFF") ) {
			sequencer.recordDisable( track );
		}
		else if( recordingChannel.equals("ALL") ) {
			sequencer.recordEnable( track, -1 );
		}
		else {
			try {
				int ch = Integer.decode(recordingChannel).intValue() - 1;
				sequencer.recordEnable( track, ch );
			} catch( NumberFormatException nfe ) {
				sequencer.recordDisable( track );
				this.recordingChannel = "OFF";
				return;
			}
		}
		this.recordingChannel = recordingChannel;
	}
	/**
	 * このトラックの対象MIDIチャンネルを返します。
	 * <p>全てのチャンネルメッセージが同じMIDIチャンネルの場合、
	 * そのMIDIチャンネルを返します。
	 * MIDIチャンネルの異なるチャンネルメッセージが一つでも含まれていた場合、
	 * -1 を返します。
	 * </p>
	 * @return 対象MIDIチャンネル（不統一の場合 -1）
	 */
	public int getChannel() {
		int prevCh = -1;
		int trackSize = track.size();
		for( int index=0; index < trackSize; index++ ) {
			MidiMessage msg = track.get(index).getMessage();
			if( ! (msg instanceof ShortMessage) )
				continue;
			ShortMessage smsg = (ShortMessage)msg;
			if( ! MIDISpec.isChannelMessage(smsg) )
				continue;
			int ch = smsg.getChannel();
			if( prevCh >= 0 && prevCh != ch ) {
				return -1;
			}
			prevCh = ch;
		}
		return prevCh;
	}
	/**
	 * 指定されたMIDIチャンネルをすべてのチャンネルメッセージに対して設定します。
	 * @param channel MIDIチャンネル
	 */
	public void setChannel(int channel) {
		int track_size = track.size();
		for( int index=0; index < track_size; index++ ) {
			MidiMessage msg = track.get(index).getMessage();
			if( ! (msg instanceof ShortMessage) )
				continue;
			ShortMessage smsg = (ShortMessage)msg;
			if( ! MIDISpec.isChannelMessage(smsg) )
				continue;
			if( smsg.getChannel() == channel )
				continue;
			try {
				smsg.setMessage(
					smsg.getCommand(), channel,
					smsg.getData1(), smsg.getData2()
				);
			}
			catch( InvalidMidiDataException e ) {
				e.printStackTrace();
			}
			sequenceTrackListTableModel.setModified(true);
		}
		sequenceTrackListTableModel.fireTrackChanged(track);
		fireTableDataChanged();
	}
	/**
	 * 指定の MIDI tick 位置にあるイベントを二分探索し、
	 * そのイベントの行インデックスを返します。
	 * @param tick MIDI tick
	 * @return 行インデックス
	 */
	public int tickToIndex(long tick) {
		if( track == null )
			return 0;
		int minIndex = 0;
		int maxIndex = track.size() - 1;
		while( minIndex < maxIndex ) {
			int currentIndex = (minIndex + maxIndex) / 2 ;
			long currentTick = track.get(currentIndex).getTick();
			if( tick > currentTick ) {
				minIndex = currentIndex + 1;
			}
			else if( tick < currentTick ) {
				maxIndex = currentIndex - 1;
			}
			else {
				return currentIndex;
			}
		}
		return (minIndex + maxIndex) / 2;
	}
	/**
	 * NoteOn/NoteOff ペアの一方の行インデックスから、
	 * もう一方（ペアの相手）の行インデックスを返します。
	 * @param index 行インデックス
	 * @return ペアを構成する相手の行インデックス（ない場合は -1）
	 */
	public int getIndexOfPartnerFor(int index) {
		if( track == null || index >= track.size() )
			return -1;
		MidiMessage msg = track.get(index).getMessage();
		if( ! (msg instanceof ShortMessage) ) return -1;
		ShortMessage sm = (ShortMessage)msg;
		int cmd = sm.getCommand();
		int i;
		int ch = sm.getChannel();
		int note = sm.getData1();
		MidiMessage partner_msg;
		ShortMessage partner_sm;
		int partner_cmd;

		switch( cmd ) {
		case 0x90: // NoteOn
		if( sm.getData2() > 0 ) {
			// Search NoteOff event forward
			for( i = index + 1; i < track.size(); i++ ) {
				partner_msg = track.get(i).getMessage();
				if( ! (partner_msg instanceof ShortMessage ) ) continue;
				partner_sm = (ShortMessage)partner_msg;
				partner_cmd = partner_sm.getCommand();
				if( partner_cmd != 0x80 && partner_cmd != 0x90 ||
						partner_cmd == 0x90 && partner_sm.getData2() > 0
						) {
					// Not NoteOff
					continue;
				}
				if( ch != partner_sm.getChannel() || note != partner_sm.getData1() ) {
					// Not my partner
					continue;
				}
				return i;
			}
			break;
		}
		// When velocity is 0, it means Note Off, so no break.
		case 0x80: // NoteOff
			// Search NoteOn event backward
			for( i = index - 1; i >= 0; i-- ) {
				partner_msg = track.get(i).getMessage();
				if( ! (partner_msg instanceof ShortMessage ) ) continue;
				partner_sm = (ShortMessage)partner_msg;
				partner_cmd = partner_sm.getCommand();
				if( partner_cmd != 0x90 || partner_sm.getData2() <= 0 ) {
					// Not NoteOn
					continue;
				}
				if( ch != partner_sm.getChannel() || note != partner_sm.getData1() ) {
					// Not my partner
					continue;
				}
				return i;
			}
			break;
		}
		// Not found
		return -1;
	}
	/**
	 * ノートメッセージかどうか調べます。
	 * @param index 行インデックス
	 * @return Note On または Note Off のとき true
	 */
	public boolean isNote(int index) {
		MidiEvent midiEvent = getMidiEvent(index);
		MidiMessage msg = midiEvent.getMessage();
		if( ! (msg instanceof ShortMessage) ) return false;
		int cmd = ((ShortMessage)msg).getCommand();
		return cmd == ShortMessage.NOTE_ON || cmd == ShortMessage.NOTE_OFF ;
	}
	/**
	 * 指定の行インデックスのMIDIイベントを返します。
	 * @param index 行インデックス
	 * @return MIDIイベント
	 */
	public MidiEvent getMidiEvent(int index) {
		return track==null ? null : track.get(index);
	}
	/**
	 * 選択されているMIDIイベントを返します。
	 * @return 選択されているMIDIイベント
	 */
	public MidiEvent[] getSelectedMidiEvents() {
		Vector<MidiEvent> events = new Vector<MidiEvent>();
		if( ! eventSelectionModel.isSelectionEmpty() ) {
			int i = eventSelectionModel.getMinSelectionIndex();
			int max = eventSelectionModel.getMaxSelectionIndex();
			for( ; i <= max; i++ )
				if( eventSelectionModel.isSelectedIndex(i) )
					events.add(track.get(i));
		}
		return events.toArray(new MidiEvent[1]);
	}
	/**
	 * MIDIイベントを追加します。
	 * @param midiEvent 追加するMIDIイベント
	 * @return 追加できたらtrue
	 */
	public boolean addMidiEvent(MidiEvent midiEvent) {
		if( track == null || !(track.add(midiEvent)) )
			return false;
		if( MIDISpec.isTimeSignature(midiEvent.getMessage()) )
			sequenceTrackListTableModel.fireTimeSignatureChanged();
		sequenceTrackListTableModel.fireTrackChanged(track);
		int lastIndex = track.size() - 1;
		fireTableRowsInserted( lastIndex-1, lastIndex-1 );
		return true;
	}
	/**
	 * MIDIイベントを追加します。
	 * @param midiEvents 追加するMIDIイベント
	 * @param destinationTick 追加先tick
	 * @param sourcePPQ PPQ値（タイミング解像度）
	 * @return 追加できたらtrue
	 */
	public boolean addMidiEvents(MidiEvent midiEvents[], long destinationTick, int sourcePPQ) {
		if( track == null )
			return false;
		int destinationPPQ = sequenceTrackListTableModel.getSequence().getResolution();
		boolean done = false;
		boolean hasTimeSignature = false;
		long firstSourceEventTick = -1;
		for( MidiEvent sourceEvent : midiEvents ) {
			long sourceEventTick = sourceEvent.getTick();
			MidiMessage msg = sourceEvent.getMessage();
			long newTick = destinationTick;
			if( firstSourceEventTick < 0 ) {
				firstSourceEventTick = sourceEventTick;
			}
			else {
				newTick += (sourceEventTick - firstSourceEventTick) * destinationPPQ / sourcePPQ;
			}
			if( ! track.add(new MidiEvent(msg, newTick)) ) continue;
			done = true;
			if( MIDISpec.isTimeSignature(msg) ) hasTimeSignature = true;
		}
		if( done ) {
			if( hasTimeSignature ) sequenceTrackListTableModel.fireTimeSignatureChanged();
			sequenceTrackListTableModel.fireTrackChanged(track);
			int lastIndex = track.size() - 1;
			int oldLastIndex = lastIndex - midiEvents.length;
			fireTableRowsInserted(oldLastIndex, lastIndex);
		}
		return done;
	}
	/**
	 * MIDIイベントを除去します。
	 * 曲の長さが変わることがあるので、プレイリストにも通知します。
	 * @param midiEvents 除去するMIDIイベント
	 */
	public void removeMidiEvents(MidiEvent midiEvents[]) {
		if( track == null )
			return;
		boolean hadTimeSignature = false;
		for( MidiEvent e : midiEvents ) {
			if( MIDISpec.isTimeSignature(e.getMessage()) )
				hadTimeSignature = true;
			track.remove(e);
		}
		if( hadTimeSignature ) {
			sequenceTrackListTableModel.fireTimeSignatureChanged();
		}
		sequenceTrackListTableModel.fireTrackChanged(track);
		int lastIndex = track.size() - 1;
		int oldLastIndex = lastIndex + midiEvents.length;
		if(lastIndex < 0) lastIndex = 0;
		fireTableRowsDeleted(oldLastIndex, lastIndex);
		sequenceTrackListTableModel.sequenceListTableModel.fireSelectedSequenceModified();
	}
	/**
	 * 引数の選択内容が示すMIDIイベントを除去します。
	 * @param selectionModel 選択内容
	 */
	public void removeSelectedMidiEvents() {
		removeMidiEvents(getSelectedMidiEvents());
	}
	private static boolean isRhythmPart(int ch) { return (ch == 9); }
	/**
	 * MIDIメッセージの内容を文字列で返します。
	 * @param msg MIDIメッセージ
	 * @return MIDIメッセージの内容を表す文字列
	 */
	public static String msgToString(MidiMessage msg, Charset charset) {
		String str = "";
		if( msg instanceof ShortMessage ) {
			ShortMessage shortmsg = (ShortMessage)msg;
			int status = msg.getStatus();
			String statusName = MIDISpec.getStatusName(status);
			int data1 = shortmsg.getData1();
			int data2 = shortmsg.getData2();
			if( MIDISpec.isChannelMessage(status) ) {
				int channel = shortmsg.getChannel();
				String channelPrefix = "Ch."+(channel+1) + ": ";
				String statusPrefix = (
					statusName == null ? String.format("status=0x%02X",status) : statusName
				) + ": ";
				int cmd = shortmsg.getCommand();
				switch( cmd ) {
				case ShortMessage.NOTE_OFF:
				case ShortMessage.NOTE_ON:
					str += channelPrefix + statusPrefix + data1;
					str += ":[";
					if( isRhythmPart(channel) ) {
						str += MIDISpec.getPercussionName(data1);
					}
					else {
						str += NoteSymbol.noteNoToSymbol(data1);
					}
					str +="] Velocity=" + data2;
					break;
				case ShortMessage.POLY_PRESSURE:
					str += channelPrefix + statusPrefix + "Note=" + data1 + " Pressure=" + data2;
					break;
				case ShortMessage.PROGRAM_CHANGE:
					str += channelPrefix + statusPrefix + data1 + ":[" + MIDISpec.instrument_names[data1] + "]";
					if( data2 != 0 ) str += " data2=" + data2;
					break;
				case ShortMessage.CHANNEL_PRESSURE:
					str += channelPrefix + statusPrefix + data1;
					if( data2 != 0 ) str += " data2=" + data2;
					break;
				case ShortMessage.PITCH_BEND:
				{
					int val = ((data1 & 0x7F) | ((data2 & 0x7F) << 7));
					str += channelPrefix + statusPrefix + ( (val-8192) * 100 / 8191) + "% (" + val + ")";
				}
				break;
				case ShortMessage.CONTROL_CHANGE:
				{
					// Control / Mode message name
					String ctrl_name = MIDISpec.getControllerName(data1);
					str += channelPrefix + (data1 < 0x78 ? "CtrlChg: " : "ModeMsg: ");
					if( ctrl_name == null ) {
						str += " No.=" + data1 + " Value=" + data2;
						return str;
					}
					str += ctrl_name;
					//
					// Controller's value
					switch( data1 ) {
					case 0x40: case 0x41: case 0x42: case 0x43: case 0x45:
						str += " " + ( data2==0x3F?"OFF":data2==0x40?"ON":data2 );
						break;
					case 0x44: // Legato Footswitch
						str += " " + ( data2==0x3F?"Normal":data2==0x40?"Legato":data2 );
						break;
					case 0x7A: // Local Control
						str += " " + ( data2==0x00?"OFF":data2==0x7F?"ON":data2 );
						break;
					default:
						str += " " + data2;
						break;
					}
				}
				break;

				default:
					// Never reached here
					break;
				}
			}
			else { // System Message
				str += (statusName == null ? ("status="+status) : statusName );
				str += " (" + data1 + "," + data2 + ")";
			}
			return str;
		}
		else if( msg instanceof MetaMessage ) {
			MetaMessage metamsg = (MetaMessage)msg;
			byte[] msgdata = metamsg.getData();
			int msgtype = metamsg.getType();
			str += "Meta: ";
			String meta_name = MIDISpec.getMetaName(msgtype);
			if( meta_name == null ) {
				str += "Unknown MessageType="+msgtype + " Values=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				return str;
			}
			// Add the message type name
			str += meta_name;
			//
			// Add the text data
			if( MIDISpec.hasMetaText(msgtype) ) {
				str +=" ["+(new String(msgdata,charset))+"]";
				return str;
			}
			// Add the numeric data
			switch(msgtype) {
			case 0x00: // Sequence Number (for MIDI Format 2）
				if( msgdata.length == 2 ) {
					str += String.format(
						": %04X",
						((msgdata[0] & 0xFF) << 8) | (msgdata[1] & 0xFF)
					);
					break;
				}
				str += ": Size not 2 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x20: // MIDI Ch.Prefix
			case 0x21: // MIDI Output Port
				if( msgdata.length == 1 ) {
					str += String.format( ": %02X", msgdata[0] & 0xFF );
					break;
				}
				str += ": Size not 1 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x51: // Tempo
				str += ": " + MIDISpec.byteArrayToQpmTempo( msgdata ) + "[QPM] (";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x54: // SMPTE Offset
				if( msgdata.length == 5 ) {
					str += ": "
						+ (msgdata[0] & 0xFF) + ":"
						+ (msgdata[1] & 0xFF) + ":"
						+ (msgdata[2] & 0xFF) + "."
						+ (msgdata[3] & 0xFF) + "."
						+ (msgdata[4] & 0xFF);
					break;
				}
				str += ": Size not 5 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x58: // Time Signature
				if( msgdata.length == 4 ) {
					str +=": " + msgdata[0] + "/" + (1 << msgdata[1]);
					str +=", "+msgdata[2]+"[clk/beat], "+msgdata[3]+"[32nds/24clk]";
					break;
				}
				str += ": Size not 4 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x59: // Key Signature
				if( msgdata.length == 2 ) {
					Key key = new Key(msgdata);
					str += ": " + key.signatureDescription();
					str += " (" + key.toStringIn(SymbolLanguage.NAME) + ")";
					break;
				}
				str += ": Size not 2 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x7F: // Sequencer Specific Meta Event
				str += " (";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			}
			return str;
		}
		else if( msg instanceof SysexMessage ) {
			SysexMessage sysexmsg = (SysexMessage)msg;
			int status = sysexmsg.getStatus();
			byte[] msgdata = sysexmsg.getData();
			int dataBytePos = 1;
			switch( status ) {
			case SysexMessage.SYSTEM_EXCLUSIVE:
				str += "SysEx: ";
				break;
			case SysexMessage.SPECIAL_SYSTEM_EXCLUSIVE:
				str += "SysEx(Special): ";
				break;
			default:
				str += "SysEx: Invalid (status="+status+") ";
				break;
			}
			if( msgdata.length < 1 ) {
				str += " Invalid data size: " + msgdata.length;
				return str;
			}
			int manufacturerId = (int)(msgdata[0] & 0xFF);
			int deviceId = (int)(msgdata[1] & 0xFF);
			int modelId = (int)(msgdata[2] & 0xFF);
			String manufacturerName = MIDISpec.SYSEX_MANUFACTURER_NAMES.get(manufacturerId);
			if( manufacturerName == null ) {
				manufacturerName = String.format("[Manufacturer code %02X]", msgdata[0]);
			}
			str += manufacturerName + String.format(" (DevID=0x%02X)", deviceId);
			switch( manufacturerId ) {
			case 0x7E: // Non-Realtime Universal
				dataBytePos++;
				int sub_id_1 = (int)(msgdata[2] & 0xFF);
				int sub_id_2 = (int)(msgdata[3] & 0xFF);
				switch( sub_id_1 ) {
				case 0x09: // General MIDI (GM)
					switch( sub_id_2 ) {
					case 0x01: str += " GM System ON"; return str;
					case 0x02: str += " GM System OFF"; return str;
					}
					break;
				default:
					break;
				}
				break;
				// case 0x7F: // Realtime Universal
			case 0x41: // Roland
				dataBytePos++;
				switch( modelId ) {
				case 0x42:
					str += " [GS]"; dataBytePos++;
					if( msgdata[3]==0x12 ) {
						str += "DT1:"; dataBytePos++;
						switch( msgdata[4] ) {
						case 0x00:
							if( msgdata[5]==0x00 ) {
								if( msgdata[6]==0x7F ) {
									if( msgdata[7]==0x00 ) {
										str += " [88] System Mode Set (Mode 1: Single Module)"; return str;
									}
									else if( msgdata[7]==0x01 ) {
										str += " [88] System Mode Set (Mode 2: Double Module)"; return str;
									}
								}
							}
							else if( msgdata[5]==0x01 ) {
								int port = (msgdata[7] & 0xFF);
								str += String.format(
										" [88] Ch.Msg Rx Port: Block=0x%02X, Port=%s",
										msgdata[6],
										port==0?"A":port==1?"B":String.format("0x%02X",port)
										);
								return str;
							}
							break;
						case 0x40:
							if( msgdata[5]==0x00 ) {
								switch( msgdata[6] ) {
								case 0x00: str += " Master Tune: "; dataBytePos += 3; break;
								case 0x04: str += " Master Volume: "; dataBytePos += 3; break;
								case 0x05: str += " Master Key Shift: "; dataBytePos += 3; break;
								case 0x06: str += " Master Pan: "; dataBytePos += 3; break;
								case 0x7F:
									switch( msgdata[7] ) {
									case 0x00: str += " GS Reset"; return str;
									case 0x7F: str += " Exit GS Mode"; return str;
									}
									break;
								}
							}
							else if( msgdata[5]==0x01 ) {
								switch( msgdata[6] ) {
								// case 0x00: str += ""; break;
								// case 0x10: str += ""; break;
								case 0x30: str += " Reverb Macro: "; dataBytePos += 3; break;
								case 0x31: str += " Reverb Character: "; dataBytePos += 3; break;
								case 0x32: str += " Reverb Pre-LPF: "; dataBytePos += 3; break;
								case 0x33: str += " Reverb Level: "; dataBytePos += 3; break;
								case 0x34: str += " Reverb Time: "; dataBytePos += 3; break;
								case 0x35: str += " Reverb Delay FB: "; dataBytePos += 3; break;
								case 0x36: str += " Reverb Chorus Level: "; dataBytePos += 3; break;
								case 0x37: str += " [88] Reverb Predelay Time: "; dataBytePos += 3; break;
								case 0x38: str += " Chorus Macro: "; dataBytePos += 3; break;
								case 0x39: str += " Chorus Pre-LPF: "; dataBytePos += 3; break;
								case 0x3A: str += " Chorus Level: "; dataBytePos += 3; break;
								case 0x3B: str += " Chorus FB: "; dataBytePos += 3; break;
								case 0x3C: str += " Chorus Delay: "; dataBytePos += 3; break;
								case 0x3D: str += " Chorus Rate: "; dataBytePos += 3; break;
								case 0x3E: str += " Chorus Depth: "; dataBytePos += 3; break;
								case 0x3F: str += " Chorus Send Level To Reverb: "; dataBytePos += 3; break;
								case 0x40: str += " [88] Chorus Send Level To Delay: "; dataBytePos += 3; break;
								case 0x50: str += " [88] Delay Macro: "; dataBytePos += 3; break;
								case 0x51: str += " [88] Delay Pre-LPF: "; dataBytePos += 3; break;
								case 0x52: str += " [88] Delay Time Center: "; dataBytePos += 3; break;
								case 0x53: str += " [88] Delay Time Ratio Left: "; dataBytePos += 3; break;
								case 0x54: str += " [88] Delay Time Ratio Right: "; dataBytePos += 3; break;
								case 0x55: str += " [88] Delay Level Center: "; dataBytePos += 3; break;
								case 0x56: str += " [88] Delay Level Left: "; dataBytePos += 3; break;
								case 0x57: str += " [88] Delay Level Right: "; dataBytePos += 3; break;
								case 0x58: str += " [88] Delay Level: "; dataBytePos += 3; break;
								case 0x59: str += " [88] Delay FB: "; dataBytePos += 3; break;
								case 0x5A: str += " [88] Delay Send Level To Reverb: "; dataBytePos += 3; break;
								}
							}
							else if( msgdata[5]==0x02 ) {
								switch( msgdata[6] ) {
								case 0x00: str += " [88] EQ Low Freq: "; dataBytePos += 3; break;
								case 0x01: str += " [88] EQ Low Gain: "; dataBytePos += 3; break;
								case 0x02: str += " [88] EQ High Freq: "; dataBytePos += 3; break;
								case 0x03: str += " [88] EQ High Gain: "; dataBytePos += 3; break;
								}
							}
							else if( msgdata[5]==0x03 ) {
								if( msgdata[6] == 0x00 ) {
									str += " [Pro] EFX Type: "; dataBytePos += 3;
								}
								else if( msgdata[6] >= 0x03 && msgdata[6] <= 0x16 ) {
									str += String.format(" [Pro] EFX Param %d", msgdata[6]-2 );
									dataBytePos += 3;
								}
								else if( msgdata[6] == 0x17 ) {
									str += " [Pro] EFX Send Level To Reverb: "; dataBytePos += 3;
								}
								else if( msgdata[6] == 0x18 ) {
									str += " [Pro] EFX Send Level To Chorus: "; dataBytePos += 3;
								}
								else if( msgdata[6] == 0x19 ) {
									str += " [Pro] EFX Send Level To Delay: "; dataBytePos += 3;
								}
								else if( msgdata[6] == 0x1B ) {
									str += " [Pro] EFX Ctrl Src1: "; dataBytePos += 3;
								}
								else if( msgdata[6] == 0x1C ) {
									str += " [Pro] EFX Ctrl Depth1: "; dataBytePos += 3;
								}
								else if( msgdata[6] == 0x1D ) {
									str += " [Pro] EFX Ctrl Src2: "; dataBytePos += 3;
								}
								else if( msgdata[6] == 0x1E ) {
									str += " [Pro] EFX Ctrl Depth2: "; dataBytePos += 3;
								}
								else if( msgdata[6] == 0x1F ) {
									str += " [Pro] EFX Send EQ Switch: "; dataBytePos += 3;
								}
							}
							else if( (msgdata[5] & 0xF0) == 0x10 ) {
								int ch = (msgdata[5] & 0x0F);
								if( ch <= 9 ) ch--; else if( ch == 0 ) ch = 9;
								if( msgdata[6]==0x02 ) {
									str += String.format(
											" Rx Ch: Part=%d(0x%02X) Ch=0x%02X", (ch+1),  msgdata[5], msgdata[7]
											);
									return str;
								}
								else if( msgdata[6]==0x15 ) {
									String map;
									switch( msgdata[7] ) {
									case 0: map = " NormalPart"; break;
									case 1: map = " DrumMap1"; break;
									case 2: map = " DrumMap2"; break;
									default: map = String.format("0x%02X",msgdata[7]); break;
									}
									str += String.format(
											" Rhythm Part: Ch=%d(0x%02X) Map=%s",
											(ch+1), msgdata[5],
											map
											);
									return str;
								}
							}
							else if( (msgdata[5] & 0xF0) == 0x40 ) {
								int ch = (msgdata[5] & 0x0F);
								if( ch <= 9 ) ch--; else if( ch == 0 ) ch = 9;
								int dt = (msgdata[7] & 0xFF);
								if( msgdata[6]==0x20 ) {
									str += String.format(
											" [88] EQ: Ch=%d(0x%02X) %s",
											(ch+1), msgdata[5],
											dt==0 ? "OFF" : dt==1 ? "ON" : String.format("0x%02X",dt)
											);
								}
								else if( msgdata[6]==0x22 ) {
									str += String.format(
											" [Pro] Part EFX Assign: Ch=%d(0x%02X) %s",
											(ch+1), msgdata[5],
											dt==0 ? "ByPass" : dt==1 ? "EFX" : String.format("0x%02X",dt)
											);
								}
							}
							break;
						} // [4]
					} // [3] [DT1]
					break; // [GS]
				case 0x45:
					str += " [GS-LCD]"; dataBytePos++;
					if( msgdata[3]==0x12 ) {
						str += " [DT1]"; dataBytePos++;
						if( msgdata[4]==0x10 && msgdata[5]==0x00 && msgdata[6]==0x00 ) {
							dataBytePos += 3;
							str += " Disp [" +(new String(
									msgdata, dataBytePos, msgdata.length - dataBytePos - 2
									))+ "]";
						}
					} // [3] [DT1]
					break;
				case 0x14: str += " [D-50]"; dataBytePos++; break;
				case 0x16: str += " [MT-32]"; dataBytePos++; break;
				} // [2] model_id
				break;
			case 0x43: // Yamaha (XG)
				dataBytePos++;
				if( modelId == 0x4C ) {
					str += " [XG]";
					if( msgdata[3]==0 && msgdata[4]==0 && msgdata[5]==0x7E && msgdata[6]==0 ) {
						str += " XG System ON"; return str;
					}
					dataBytePos++;
				}
				break;
			default:
				break;
			}
			int i;
			str += " data=(";
			for( i = dataBytePos; i<msgdata.length-1; i++ ) {
				str += String.format( " %02X", msgdata[i] );
			}
			if( i < msgdata.length && (int)(msgdata[i] & 0xFF) != 0xF7 ) {
				str+=" [ Invalid EOX " + String.format( "%02X", msgdata[i] ) + " ]";
			}
			str += " )";
			return str;
		}
		byte[] msg_data = msg.getMessage();
		str += "(";
		for( byte b : msg_data ) {
			str += String.format( " %02X", b );
		}
		str += " )";
		return str;
	}

	/**
	 *  MIDI シーケンスデータのtickインデックス
	 * <p>拍子、テンポ、調だけを抜き出したトラックを保持するためのインデックスです。
	 * 指定の MIDI tick の位置におけるテンポ、調、拍子を取得したり、
	 * 拍子情報から MIDI tick と小節位置との間の変換を行うために使います。
	 * </p>
	 */
	public static class SequenceTickIndex {
		/**
		 * メタメッセージの種類：テンポ
		 */
		public static final int TEMPO = 0;
		/**
		 * メタメッセージの種類：拍子
		 */
		public static final int TIME_SIGNATURE = 1;
		/**
		 * メタメッセージの種類：調号
		 */
		public static final int KEY_SIGNATURE = 2;
		/**
		 * メタメッセージタイプ → メタメッセージの種類 変換マップ
		 */
		private static final Map<Integer,Integer> INDEX_META_TO_TRACK =
			new HashMap<Integer,Integer>() {
				{
					put(0x51, TEMPO);
					put(0x58, TIME_SIGNATURE);
					put(0x59, KEY_SIGNATURE);
				}
			};
		/**
		 * 新しいMIDIシーケンスデータのインデックスを構築します。
		 * @param sourceSequence 元のMIDIシーケンス
		 */
		public SequenceTickIndex(Sequence sourceSequence) {
			try {
				int ppq = sourceSequence.getResolution();
				wholeNoteTickLength = ppq * 4;
				tmpSequence = new Sequence(Sequence.PPQ, ppq, 3);
				tracks = tmpSequence.getTracks();
				Track[] sourceTracks = sourceSequence.getTracks();
				for( Track tk : sourceTracks ) {
					for( int i_evt = 0 ; i_evt < tk.size(); i_evt++ ) {
						MidiEvent evt = tk.get(i_evt);
						MidiMessage msg = evt.getMessage();
						if( ! (msg instanceof MetaMessage) )
							continue;
						MetaMessage metaMsg = (MetaMessage)msg;
						int metaType = metaMsg.getType();
						Integer metaIndex = INDEX_META_TO_TRACK.get(metaType);
						if( metaIndex != null ) tracks[metaIndex].add(evt);
					}
				}
			}
			catch ( InvalidMidiDataException e ) {
				e.printStackTrace();
			}
		}
		private Sequence tmpSequence;
		/**
		 * このtickインデックスのタイミング解像度を返します。
		 * @return このtickインデックスのタイミング解像度
		 */
		public int getResolution() {
			return tmpSequence.getResolution();
		}
		private Track[] tracks;
		/**
		 * 指定されたtick位置以前の最後のメタメッセージを返します。
		 * @param trackIndex メタメッセージの種類（）
		 * @param tickPosition
		 * @return
		 */
		public MetaMessage lastMetaMessageAt(int trackIndex, long tickPosition) {
			Track track = tracks[trackIndex];
			for(int eventIndex = track.size()-1 ; eventIndex >= 0; eventIndex--) {
				MidiEvent event = track.get(eventIndex);
				if( event.getTick() > tickPosition )
					continue;
				MetaMessage metaMessage = (MetaMessage)(event.getMessage());
				if( metaMessage.getType() == 0x2F /* skip EOT (last event) */ )
					continue;
				return metaMessage;
			}
			return null;
		}

		private int wholeNoteTickLength;
		public int lastBeat;
		public int lastExtraTick;
		public byte timesigUpper;
		public byte timesigLowerIndex;
		/**
		 * tick位置を小節位置に変換します。
		 * @param tickPosition tick位置
		 * @return 小節位置
		 */
		public int tickToMeasure(long tickPosition) {
			byte extraBeats = 0;
			MidiEvent event = null;
			MidiMessage message = null;
			byte[] data = null;
			long currentTick = 0L;
			long nextTimesigTick = 0L;
			long prevTick = 0L;
			long duration = 0L;
			int lastMeasure = 0;
			int eventIndex = 0;
			timesigUpper = 4;
			timesigLowerIndex = 2; // =log2(4)
			if( tracks[TIME_SIGNATURE] != null ) {
				do {
					// Check current time-signature event
					if( eventIndex < tracks[TIME_SIGNATURE].size() ) {
						message = (event = tracks[TIME_SIGNATURE].get(eventIndex)).getMessage();
						currentTick = nextTimesigTick = event.getTick();
						if(currentTick > tickPosition || (message.getStatus() == 0xFF && ((MetaMessage)message).getType() == 0x2F /* EOT */)) {
							currentTick = tickPosition;
						}
					}
					else { // No event
						currentTick = nextTimesigTick = tickPosition;
					}
					// Add measure from last event
					//
					int beatTickLength = wholeNoteTickLength >> timesigLowerIndex;
					duration = currentTick - prevTick;
					int beats = (int)( duration / beatTickLength );
					lastExtraTick = (int)(duration % beatTickLength);
					int measures = beats / timesigUpper;
					extraBeats = (byte)(beats % timesigUpper);
					lastMeasure += measures;
					if( nextTimesigTick > tickPosition ) break;  // Not reached to next time signature
					//
					// Reached to the next time signature, so get it.
					if( ( data = ((MetaMessage)message).getData() ).length > 0 ) { // To skip EOT, check the data length.
						timesigUpper = data[0];
						timesigLowerIndex = data[1];
					}
					if( currentTick == tickPosition )  break;  // Calculation complete
					//
					// Calculation incomplete, so prepare for next
					//
					if( extraBeats > 0 ) {
						//
						// Extra beats are treated as 1 measure
						lastMeasure++;
					}
					prevTick = currentTick;
					eventIndex++;
				} while( true );
			}
			lastBeat = extraBeats;
			return lastMeasure;
		}
		/**
		 * 小節位置を MIDI tick に変換します。
		 * @param measure 小節位置
		 * @return MIDI tick
		 */
		public long measureToTick(int measure) {
			return measureToTick(measure, 0, 0);
		}
		/**
		 * 指定の小節位置、拍、拍内tickを、そのシーケンス全体の MIDI tick に変換します。
		 * @param measure 小節位置
		 * @param beat 拍
		 * @param extraTick 拍内tick
		 * @return そのシーケンス全体の MIDI tick
		 */
		public long measureToTick(int measure, int beat, int extraTick) {
			MidiEvent evt = null;
			MidiMessage msg = null;
			byte[] data = null;
			long tick = 0L;
			long prev_tick = 0L;
			long duration = 0L;
			long duration_sum = 0L;
			long estimated_ticks;
			int ticks_per_beat;
			int i_evt = 0;
			timesigUpper = 4;
			timesigLowerIndex = 2; // =log2(4)
			do {
				ticks_per_beat = wholeNoteTickLength >> timesigLowerIndex;
				estimated_ticks = ((measure * timesigUpper) + beat) * ticks_per_beat + extraTick;
				if( tracks[TIME_SIGNATURE] == null || i_evt > tracks[TIME_SIGNATURE].size() ) {
					return duration_sum + estimated_ticks;
				}
				msg = (evt = tracks[TIME_SIGNATURE].get(i_evt)).getMessage();
				if( msg.getStatus() == 0xFF && ((MetaMessage)msg).getType() == 0x2F /* EOT */ ) {
					return duration_sum + estimated_ticks;
				}
				duration = (tick = evt.getTick()) - prev_tick;
				if( duration >= estimated_ticks ) {
					return duration_sum + estimated_ticks;
				}
				// Re-calculate measure (ignore extra beats/ticks)
				measure -= ( duration / (ticks_per_beat * timesigUpper) );
				duration_sum += duration;
				//
				// Get next time-signature
				data = ( (MetaMessage)msg ).getData();
				timesigUpper = data[0];
				timesigLowerIndex = data[1];
				prev_tick = tick;
				i_evt++;
			} while( true );
		}
	}
}