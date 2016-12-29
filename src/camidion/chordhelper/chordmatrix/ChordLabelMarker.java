package camidion.chordhelper.chordmatrix;

import camidion.chordhelper.chordmatrix.ChordMatrix.ChordLabel;

class ChordLabelMarker {
	private ChordLabel chordLabel;
	private int bitIndex;
	private boolean useBass;
	public ChordLabelMarker(ChordLabel chordLabel, int bitIndex) {
		this.chordLabel = chordLabel;
		this.bitIndex = bitIndex;
		this.useBass = (bitIndex == 0 && ! chordLabel.isSus4);
	}
	public void mark(boolean isOn) { chordLabel.setCheckBit(isOn, bitIndex); }
	public boolean markBass(boolean isOn) {
		if( !useBass ) return false;
		chordLabel.setCheckBit(isOn, 6); return true;
	}
}
