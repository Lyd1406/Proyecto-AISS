package aiss.videominer.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import aiss.videominer.model.Channel;
import aiss.videominer.repository.ChannelRepository;

@Controller
@RequestMapping("/web")
public class WebController {

    @Autowired
    ChannelRepository channelRepository;

    @GetMapping("/channels")
    public String listChannels(Model model) {
        List<Channel> channels = channelRepository.findAll();
        model.addAttribute("channels", channels);
        return "channels";
    }

    @GetMapping("/channels/{id}")
    public String channelDetail(@PathVariable String id, Model model) {
        Optional<Channel> channel = channelRepository.findById(id);
        if (channel.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found");
        model.addAttribute("channel", channel.get());
        return "channel-detail";
    }
}