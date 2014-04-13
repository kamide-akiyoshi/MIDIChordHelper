package camidion.chordhelper.mididevice;

import javax.sound.midi.MidiChannel;

import camidion.chordhelper.music.MIDISpec;

/**
 * MIDIチャンネルの状態を管理するクラスです。
 */
public abstract class AbstractMidiChannelStatus implements MidiChannel {
	protected int channel;
	protected int program = 0;
	protected int pitchBend = MIDISpec.PITCH_BEND_NONE;
	protected int controllerValues[] = new int[0x80];
	protected boolean isRhythmPart = false;

	protected static final int DATA_NONE = 0;
	protected static final int DATA_FOR_RPN = 1;
	protected static final int DATA_FOR_NRPN = 2;
	protected int dataFor = DATA_NONE;

	public AbstractMidiChannelStatus(int channel) {
		this.channel = channel;
		resetAllValues(true);
	}
	public int getChannel() { return channel; }
	public boolean isRhythmPart() { return isRhythmPart; }
	public void setRhythmPart(boolean isRhythmPart) {
		this.isRhythmPart = isRhythmPart;
	}
	public void resetRhythmPart() {
		isRhythmPart = (channel == 9);
	}
	public void resetAllValues() { resetAllValues(false); }
	public void resetAllValues(boolean isGS) {
		for( int i=0; i<controllerValues.length; i++ )
			controllerValues[i] = 0;
		if( isGS ) resetRhythmPart();
		resetAllControllers();
		controllerValues[10] = 0x40; // Set pan to center
	}
	public void fireRpnChanged() {}
	protected void changeRPNData( int dataDiff ) {
		int dataMsb = controllerValues[0x06];
		int dataLsb = controllerValues[0x26];
		if( dataDiff != 0 ) {
			// Data increment or decrement
			dataLsb += dataDiff;
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