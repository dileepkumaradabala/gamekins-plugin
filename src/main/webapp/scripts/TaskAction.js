jQuery3("#closeModalBtn").on("click", () => {
    jQuery3("#error-text")[0].innerHTML = ""
    jQuery3("#reason-text").val("")
})

jQuery3("#contentModal").on('show.bs.modal', function (event) {
    let modal = jQuery3(this)
    let button = jQuery3(event.relatedTarget)
    modal.find('.modal-title').text(button.data('file'))
    let fileContentDisplay = document.getElementById('fileContentDisplay')
    let highlightedSource = document.getElementById('highlightedSource' + button.data('challenge-id'))
    highlightedSource.style.display = 'block'
    fileContentDisplay.innerHTML = ""
    fileContentDisplay.appendChild(highlightedSource)
})


jQuery3("#rejectModal").on('show.bs.modal', function (event) {
    let modal = jQuery3(this)
    let button = jQuery3(event.relatedTarget)
    let descriptorFullUrl = button.data('descriptor-url')
    let rejectBtnConfirm = modal.find('#rejectButtonConfirm')
    if (button.data('challenge-id') === undefined) {
        rejectBtnConfirm.data("qid", button.data('quest-id'))
        modal.find('.modal-title').text('Reject Quest ' + button.data('quest-id'))
    } else {
        rejectBtnConfirm.data("cid", button.data('challenge-id'))
        modal.find('.modal-title').text('Reject Current Challenge ' + button.data('challenge-id'))
    }
    rejectBtnConfirm.data("url", descriptorFullUrl)
})

jQuery3("#rejectModal").on('shown.bs.modal', function () {
    jQuery3(this).find('#reason-text').focus()
})

jQuery3('#rejectButtonConfirm').on('click', function () {
    let btn = jQuery3(this)
    let questID = btn.data('qid')
    let challengeID = btn.data('cid')
    let descriptorFullUrl = btn.data('url')
    let modal = jQuery3("#rejectModal")
    let reason = modal.find('#reason-text').val()




    if (questID === undefined) {
        let challenge = jQuery3("#currentChallengeText" + challengeID)[0].innerText
        let url = descriptorFullUrl + "/rejectChallenge"

        jQuery.ajax(url, {
            data: jQuery.param({reason: reason, reject: challenge}),
            success: function (rsp) {
                if (rsp.includes("class=error")) {
                    modal.find('#error-text')[0].innerHTML = rsp;
                    return false;
                } else {
                    modal.find('#error-text')[0].innerHTML = ""
                    modal.find('#reason-text').val("")
                    // Update UI after successful rejection
                    const temp = jQuery3("#heading" + challengeID);
                    const challengeText = temp.find("#currentChallengeText" + challengeID).html();
                    if (challengeText != null) {
                        if (!challengeText.includes("You have nothing developed recently")) {
                            let appendedVal = '<tr>' +
                                '<td class="p-4" style="background-color: #fff4e8;" data-toggle="tooltip" data-placement="right" title="' + reason + '">' + challengeText + '</td>' +
                                '</tr>'
                            jQuery3("#rejectedTable").append(appendedVal)
                            jQuery3('[data-toggle="tooltip"]').tooltip()
                        }
                    }
                }
                modal.modal("hide")
                location.reload()
            }
        })
    } else {
        let quest = jQuery3("#currentQuestText" + questID)[0].innerText
        let url = descriptorFullUrl + "/rejectQuest"

        jQuery.ajax(url, {
            data: jQuery.param({reason: reason, reject: quest}),
            success: function (rsp) {
                if (rsp.includes("class=error")) {
                    modal.find('#error-text')[0].innerHTML = rsp;
                    return false;
                } else {
                    modal.find('#error-text')[0].innerHTML = ""
                    modal.find('#reason-text').val("")

                }
                modal.modal("hide")
                location.reload()
            }
        })
    }
})

jQuery3('.undoReject').on('click', function () {

    let btn = jQuery3(this)
    let challenge = btn.data('challenge-id')
    let descriptorFullUrl = btn.data('descriptor-url')
    let url = descriptorFullUrl + "/restoreChallenge"

    challenge = challenge.replaceAll('<b>', '')
    challenge = challenge.replaceAll('</b>', '')

    jQuery.ajax(url, {
        data: jQuery.param({reject: challenge}),
        success: function (rsp) {
            if (rsp.includes("class=error")) {
                jQuery3('#error-text-rejected-table')[0].innerHTML = rsp;
                return false;
            } else {
                jQuery3('#error-text-rejected-table')[0].innerHTML = ""
            }
            location.reload()
        }
    })
})

jQuery3('.storeChallenge').on('click', function () {
    let btn = jQuery3(this)
    let challengeID = btn.data('challenge-id')
    let descriptorFullUrl = btn.data('descriptor-url')

    let challenge = jQuery3("#currentChallengeText" + challengeID)[0].innerText
    let url = descriptorFullUrl + "/storeChallenge"

    jQuery.ajax(url, {
        data: jQuery.param({store: challenge}),
        success: function (rsp) {
            if (rsp.includes("class=error")) {
                jQuery3('#error-text-current-table')[0].innerHTML = rsp;
                return false;
            } else {
                jQuery3('#error-text-current-table')[0].innerHTML = ""
            }
            location.reload()
        }
    })

})

jQuery3('.undoStore').on('click', function () {

    let btn = jQuery3(this)
    let challenge = btn.data('challenge-id')
    let descriptorFullUrl = btn.data('descriptor-url')
    let url = descriptorFullUrl + "/undoStoreChallenge"

    challenge = challenge.replaceAll('<b>', '')
    challenge = challenge.replaceAll('</b>', '')

    jQuery.ajax(url, {
        data: jQuery.param({store: challenge}),
    success: function (rsp) {
        if (rsp.includes("class=error")) {
            jQuery3('#error-text-stored')[0].innerHTML = rsp;
            return false;
        } else {
            jQuery3('#error-text-stored')[0].innerHTML = ""
        }
            location.reload()
        }
    })
})

jQuery3("#sendModal").on('show.bs.modal', function (event) {
    let modal = jQuery3(this)
    let button = jQuery3(event.relatedTarget)
    modal.data('challenge-id', button.data('challenge-id'))
})

jQuery3("#sendModal").on('close.bs.modal', function (event) {
    let modal = jQuery3(this)
    modal.data('challenge-id', "")
})



jQuery3('.sendChallenge').on('click', function () {

    let btn = jQuery3(this)
    let modal = jQuery("#sendModal")
    let challenge = modal.data('challenge-id')
    let user = btn.data('user-name')
    let descriptorFullUrl = btn.data('descriptor-url')
    let url = descriptorFullUrl + "/sendChallenge"

    challenge = challenge.replaceAll('<b>', '')
    challenge = challenge.replaceAll('</b>', '')

    jQuery.ajax(url, {
        data: jQuery.param({send: challenge, to: user}),
        success: function (rsp) {
            if (rsp.includes("class=error")) {
                jQuery3('#error-text-send')[0].innerHTML = rsp;
                return false;
            } else {
                jQuery3('#error-text-send')[0].innerHTML = ""
            }
        location.reload()
        }
    })
})