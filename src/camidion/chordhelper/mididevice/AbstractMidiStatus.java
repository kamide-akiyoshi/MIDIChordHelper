package camidion.chordhelper.mididevice;

import java.util.Vector;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;

/**
 * 仮想 MIDI デバイスからの MIDI 受信とチャンネル状態の管理
 */
public abstract class AbstractMidiStatus extends Vector<AbstractMidiChannelStatus>
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