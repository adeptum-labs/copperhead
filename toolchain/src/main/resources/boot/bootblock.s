; Trackdisk loader. Kickstart reads this block from the first two sectors, checks its checksum and
; enters at offset 12 with A1 = the trackdisk request it read the block with and A6 = ExecBase.
;
; The build defines LOAD (where the program runs), PAYLOAD_LEN (the payload's bytes, padded to whole
; sectors), CRUNCHED and STAGE. A crunched payload is read to STAGE, above the program's own memory,
; and unpacked to LOAD; any other payload is read to LOAD straight away.

EXEC_DOIO		=	-456
EXEC_CACHECLEARU	=	-636
LIB_VERSION		=	20
KICKSTART_2		=	37

IO_COMMAND		=	28
IO_ERROR		=	31
IO_LENGTH		=	36
IO_DATA			=	40
IO_OFFSET		=	44

CMD_READ		=	2
TD_MOTOR		=	9
PAYLOAD_OFFSET		=	1024

; The unpacker needs 3 KB of stack, more than a boot block can count on, so it gets its own just above
; the staged data. The build reserves this memory when it checks the program fits.
DECRUNCH_STACK		=	4096

	if	CRUNCHED
DESTINATION		=	STAGE
	else
DESTINATION		=	LOAD
	endif

	dc.b	"DOS",0
	dc.l	0			; checksum, written once the block is assembled
	dc.l	880			; root block of a filing system disk, which this is not

	movem.l	d2/a2,-(a7)		; Kickstart expects them back when the disk fails to boot
	move.l	a1,a2
	move.w	#CMD_READ,IO_COMMAND(a2)
	move.l	#PAYLOAD_LEN,IO_LENGTH(a2)
	move.l	#DESTINATION,IO_DATA(a2)
	move.l	#PAYLOAD_OFFSET,IO_OFFSET(a2)
	bsr.b	request
	move.b	IO_ERROR(a2),d2		; switching the motor off overwrites it
	move.w	#TD_MOTOR,IO_COMMAND(a2)
	clr.l	IO_LENGTH(a2)		; a length of zero switches the motor off
	bsr.b	request
	tst.b	d2
	bne.b	failed

	if	CRUNCHED
	move.l	a7,a3			; the unpacker keeps A3, so the program starts on Kickstart's stack
	lea	STAGE+PAYLOAD_LEN+DECRUNCH_STACK,a7
	lea	STAGE,a0
	lea	LOAD,a1
	sub.l	a2,a2			; no progress callback
	moveq	#1,d7			; parity context, which the cruncher uses by default
	bsr.w	ShrinklerDecompress
	move.l	a3,a7
	endif

	cmp.w	#KICKSTART_2,LIB_VERSION(a6)
	blo.b	start			; Kickstart 1.x has no call to flush caches
	jsr	EXEC_CACHECLEARU(a6)	; the program arrived as data, so no cache may keep older contents
start:
	jmp	LOAD

failed:
	movem.l	(a7)+,d2/a2
	moveq	#-1,d0			; tells Kickstart that this disk did not boot
	rts

request:
	move.l	a2,a1
	jmp	EXEC_DOIO(a6)

	if	CRUNCHED
	include	"ShrinklerDecompress.S"
	endif
